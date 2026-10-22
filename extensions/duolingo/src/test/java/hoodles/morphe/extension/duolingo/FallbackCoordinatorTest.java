package hoodles.morphe.extension.duolingo;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free regression harness; run with javac/java alongside FallbackCoordinator.java. */
public final class FallbackCoordinatorTest {
    private static final class Call {
        final Object manager;
        final Object user;
        final Object language;

        Call(Object manager, Object user, Object language) {
            this.manager = manager;
            this.user = user;
            this.language = language;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        normalRegistrationDoesNotSchedule();
        earlyFailureUsesLatestBinding();
        changedUserAndLanguageAreNotStale();
        repositoryIdentityIsRespected();
        dispatchFailureCanBeRetried();
        reentrantBindingIsNotLost();
        logoutSuppressesLateFailuresAndQueuedWork();
        nullLanguageUsesNativeDefault();
        System.out.println("Passed 8 local learning reminder coordinator regression scenarios.");
    }

    private static FallbackCoordinator coordinator(List<Call> calls) {
        return new FallbackCoordinator(
                (manager, user, language) -> calls.add(new Call(manager, user, language)),
                exception -> { throw new AssertionError("Unexpected scheduling failure", exception); });
    }

    private static void normalRegistrationDoesNotSchedule() {
        List<Call> calls = new ArrayList<>();
        FallbackCoordinator coordinator = coordinator(calls);
        coordinator.bind(new Object(), new Object(), new Object(), new Object());
        require(calls.isEmpty(), "A successful registration must leave local fallback untouched");
    }

    private static void earlyFailureUsesLatestBinding() {
        List<Call> calls = new ArrayList<>();
        FallbackCoordinator coordinator = coordinator(calls);
        Object repository = new Object(), manager = new Object(), user = new Object(), language = new Object();
        coordinator.onRegistrationFailure(repository);
        coordinator.onRegistrationFailure(repository);
        require(calls.isEmpty(), "No reminder may be scheduled without user settings");
        coordinator.bind(repository, manager, user, language);
        require(calls.size() == 1, "Early errors must coalesce until settings become available");
        require(calls.get(0).user == user && calls.get(0).manager == manager,
                "Early errors must use the bound user and manager");
        coordinator.bind(repository, manager, user, language);
        require(calls.size() == 1, "An unchanged binding must not requeue a completed request");
    }

    private static void changedUserAndLanguageAreNotStale() {
        List<Call> calls = new ArrayList<>();
        FallbackCoordinator coordinator = coordinator(calls);
        Object repository = new Object(), manager = new Object(), oldUser = new Object(), oldLanguage = new Object();
        Object newUser = new Object(), newLanguage = new Object();
        coordinator.bind(repository, manager, oldUser, oldLanguage);
        coordinator.onRegistrationFailure(repository);
        coordinator.bind(repository, manager, newUser, newLanguage);
        require(calls.size() == 2 && calls.get(1).user == newUser && calls.get(1).language == newLanguage,
                "Following an error, changed home state must use current settings");
        require(!coordinator.isCurrentBinding(manager, oldUser, oldLanguage),
                "A queued task for old home state must become stale");
        require(coordinator.isCurrentBinding(manager, newUser, newLanguage),
                "A queued task for current home state must remain eligible");
    }

    private static void repositoryIdentityIsRespected() {
        List<Call> calls = new ArrayList<>();
        FallbackCoordinator coordinator = coordinator(calls);
        // Equal values are deliberately different owners.
        Object first = new String("repository"), second = new String("repository");
        Object firstUser = new Object(), secondUser = new Object(), manager = new Object(), language = new Object();
        coordinator.bind(first, manager, firstUser, language);
        coordinator.bind(second, manager, secondUser, language);
        coordinator.onRegistrationFailure(first);
        require(calls.size() == 1 && calls.get(0).user == firstUser,
                "An error must not use the binding belonging to an equal but distinct owner");
    }

    private static void dispatchFailureCanBeRetried() {
        List<Call> calls = new ArrayList<>();
        int[] attempts = {0}, failures = {0};
        FallbackCoordinator coordinator = new FallbackCoordinator((manager, user, language) -> {
            if (++attempts[0] == 1) throw new IllegalStateException("Executor unavailable");
            calls.add(new Call(manager, user, language));
        }, exception -> failures[0]++);
        Object repository = new Object(), manager = new Object(), user = new Object(), language = new Object();
        coordinator.bind(repository, manager, user, language);
        coordinator.onRegistrationFailure(repository);
        require(failures[0] == 1 && calls.isEmpty(), "Dispatch failures must be reported without escaping");
        coordinator.bind(repository, manager, user, language);
        require(calls.size() == 1 && attempts[0] == 2, "Failed requests must survive for a later retry");
    }

    private static void reentrantBindingIsNotLost() {
        List<Call> calls = new ArrayList<>();
        Object repository = new Object(), manager = new Object(), oldUser = new Object(), currentUser = new Object();
        Object language = new Object();
        FallbackCoordinator[] holder = new FallbackCoordinator[1];
        holder[0] = new FallbackCoordinator((owner, user, learningLanguage) -> {
            calls.add(new Call(owner, user, learningLanguage));
            if (user == oldUser) holder[0].bind(repository, manager, currentUser, language);
        }, exception -> { throw new AssertionError(exception); });
        holder[0].bind(repository, manager, oldUser, language);
        holder[0].onRegistrationFailure(repository);
        require(calls.size() == 2 && calls.get(1).user == currentUser,
                "A state update during dispatch must be queued with the new user");
    }

    private static void logoutSuppressesLateFailuresAndQueuedWork() {
        List<Call> calls = new ArrayList<>();
        FallbackCoordinator coordinator = coordinator(calls);
        Object repository = new Object(), manager = new Object(), user = new Object(), language = new Object();
        coordinator.bind(repository, manager, user, language);
        coordinator.onRegistrationFailure(repository);
        coordinator.unbind(repository);
        require(!coordinator.isCurrentBinding(manager, user, language),
                "Logout must invalidate work already queued for the old account");
        coordinator.onRegistrationFailure(repository);
        require(calls.size() == 1, "A late failure after logout must not reactivate local reminders");
        Object nextUser = new Object();
        coordinator.bind(repository, manager, nextUser, language);
        require(calls.size() == 1, "A prior account's error must not activate fallback for a new account");
        coordinator.onRegistrationFailure(repository);
        require(calls.size() == 2 && calls.get(1).user == nextUser,
                "A new account must remain eligible for fallback after its own failure");
    }

    private static void nullLanguageUsesNativeDefault() {
        List<Call> calls = new ArrayList<>();
        FallbackCoordinator coordinator = coordinator(calls);
        Object repository = new Object(), manager = new Object(), user = new Object();
        coordinator.bind(repository, manager, user, null);
        coordinator.onRegistrationFailure(repository);
        require(calls.size() == 1 && calls.get(0).language == null,
                "The native bridge must be allowed to resolve an unspecified language");
        require(coordinator.isCurrentBinding(manager, user, null),
                "A task using the native default language must remain eligible");
    }
}
