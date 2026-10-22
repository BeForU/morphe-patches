package hoodles.morphe.extension.duolingo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Consumer;

/** Keeps an early asynchronous FCM error until home state supplies reminder settings. */
final class FallbackCoordinator {
    interface Scheduler {
        void schedule(Object manager, Object user, Object language);
    }

    private static final class State {
        final WeakReference<Object> repository;
        Object manager;
        Object user;
        Object language;
        boolean failed;
        boolean pending;
        boolean dispatching;
        boolean unbound;

        State(Object repository) {
            this.repository = new WeakReference<>(repository);
        }
    }

    private final ArrayList<State> states = new ArrayList<>();
    private final Scheduler scheduler;
    private final Consumer<RuntimeException> failureReporter;

    FallbackCoordinator(Scheduler scheduler, Consumer<RuntimeException> failureReporter) {
        this.scheduler = scheduler;
        this.failureReporter = failureReporter;
    }

    synchronized void bind(Object repository, Object manager, Object user, Object language) {
        // A null language asks the native bridge to use User's current course language.
        if (repository == null || manager == null || user == null) return;
        State state = stateFor(repository);
        state.unbound = false;
        boolean changed = state.manager != manager || state.user != user || state.language != language;
        state.manager = manager;
        state.user = user;
        state.language = language;
        // On a new account/settings/language snapshot, use its current settings after an error.
        if (state.failed && changed) state.pending = true;
        dispatch(state);
    }

    synchronized void onRegistrationFailure(Object repository) {
        if (repository == null) return;
        State state = stateFor(repository);
        // Ignore a late registration error from an account which has already logged out.
        if (state.unbound) return;
        state.failed = true;
        state.pending = true;
        dispatch(state);
    }

    synchronized void unbind(Object repository) {
        if (repository == null) return;
        State state = stateFor(repository);
        state.manager = null;
        state.user = null;
        state.language = null;
        state.failed = false;
        state.pending = false;
        state.unbound = true;
    }

    synchronized boolean isCurrentBinding(Object manager, Object user, Object language) {
        for (State state : states) {
            if (state.repository.get() != null && state.manager == manager
                    && state.user == user && state.language == language) return true;
        }
        return false;
    }

    private State stateFor(Object repository) {
        State found = null;
        Iterator<State> iterator = states.iterator();
        while (iterator.hasNext()) {
            State state = iterator.next();
            Object owner = state.repository.get();
            if (owner == null) iterator.remove();
            else if (owner == repository) found = state;
        }
        if (found != null) return found;
        State state = new State(repository);
        states.add(state);
        return state;
    }

    private void dispatch(State state) {
        if (state.dispatching || state.manager == null || state.user == null) return;
        state.dispatching = true;
        try {
            while (state.pending) {
                state.pending = false;
                try {
                    scheduler.schedule(state.manager, state.user, state.language);
                } catch (RuntimeException exception) {
                    // Keep the request for the next bind or failure; do not alter FCM error flow.
                    state.pending = true;
                    failureReporter.accept(exception);
                    return;
                }
            }
        } finally {
            state.dispatching = false;
        }
    }
}
