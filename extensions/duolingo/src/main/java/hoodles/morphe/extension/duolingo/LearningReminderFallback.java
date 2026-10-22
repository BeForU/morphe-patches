package hoodles.morphe.extension.duolingo;

import android.util.Log;

/** Connects registration failures to Duolingo's original local reminder executor. */
public final class LearningReminderFallback {
    private static final String TAG = "MorpheDuoReminders";
    private static final FallbackCoordinator COORDINATOR = new FallbackCoordinator(
            LearningReminderFallback::schedule,
            exception -> Log.w(TAG, "Could not queue a local learning reminder", exception));

    private LearningReminderFallback() {}

    public static void bind(Object repository, Object manager, Object user, Object language) {
        COORDINATOR.bind(repository, manager, user, language);
    }

    public static void onRegistrationFailure(Object repository) {
        COORDINATOR.onRegistrationFailure(repository);
    }

    public static void unbind(Object repository) {
        COORDINATOR.unbind(repository);
    }

    /** Replaced by the patch with a call into the app's original executor. */
    public static void schedule(Object manager, Object user, Object language) {
        throw new IllegalStateException("Local reminder dispatch bridge was not patched");
    }

    /** Replaced by the patch with the app-specific scheduling implementation. */
    public static void runScheduled(Object manager, Object user, Object language) {
        throw new IllegalStateException("Local reminder scheduling bridge was not patched");
    }

    /** Runs all mutable reminder state access on the same executor as the original app. */
    public static final class Reminder implements Runnable {
        private final Object manager;
        private final Object user;
        private final Object language;

        public Reminder(Object manager, Object user, Object language) {
            this.manager = manager;
            this.user = user;
            this.language = language;
        }

        @Override
        public void run() {
            // Home state can change while the native executor still has this task queued.
            if (!COORDINATOR.isCurrentBinding(manager, user, language)) return;
            try {
                runScheduled(manager, user, language);
            } catch (RuntimeException exception) {
                // A fallback must not replace the app's original FCM error handling with a crash.
                Log.w(TAG, "Could not schedule a local learning reminder", exception);
            }
        }
    }
}
