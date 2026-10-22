package hoodles.morphe.patches.duolingo.misc.notifications

import app.morphe.patcher.Fingerprint

internal object RegistrationFailureFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("Device Registration failed", "successful"),
)

internal object HomeRegistrationFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("Failed to check FCM availability"),
)

internal object LocalReminderManagerFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf(
        "practice_notification_language_time_map",
        "local_notifications_enabled",
        "local_notifications_trumps_ab_bucket",
    ),
)

internal object ReminderSettingsFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("PracticeReminderSettings(timeInMinutes=", ", pushEnabled="),
)
