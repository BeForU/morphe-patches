package hoodles.morphe.patches.duolingo.misc.notifications

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import hoodles.morphe.compatibility.Compat

private const val EXTENSION = "Lhoodles/morphe/extension/duolingo/LearningReminderFallback;"
private const val USER = "Lcom/duolingo/data/user/User;"
private const val LANGUAGE = "Lcom/duolingo/core/language/Language;"
private const val OBJECT = "Ljava/lang/Object;"

private fun <T> Iterable<T>.unique(description: String): T = singleOrNull()
    ?: throw PatchException("Learning reminders: could not uniquely identify $description")

private inline fun <reified T : Reference> Method.references(): List<T> =
    implementation?.instructions?.mapNotNull { it.getReference<T>() }?.distinctBy { it.toString() } ?: emptyList()

private fun Method.invoke() = if (AccessFlags.PRIVATE.isSet(accessFlags)) "invoke-direct" else "invoke-virtual"

/** FCM failure is different from GMS availability, which the MicroG patch deliberately spoofs. */
@Suppress("unused")
val restoreLearningRemindersPatch = bytecodePatch(
    name = "Restore learning reminders",
    description = "Enables and reschedules local learning reminders when FCM device registration fails.",
    default = false,
) {
    compatibleWith(Compat.DUOLINGO)
    extendWith("extensions/duolingo.mpe")

    execute {
        // Resolve all original references before inserting instructions. Fail closed on unsupported APKs.
        val manager = LocalReminderManagerFingerprint.classDef
        val managerType = manager.type
        val home = HomeRegistrationFingerprint.method
        val failure = RegistrationFailureFingerprint.method
        val settingsType = ReminderSettingsFingerprint.classDef.type
        val failureIndex = failure.instructions.indexOfFirst {
            it.getReference<StringReference>()?.string == "Device Registration failed"
        }
        if (failureIndex < 0) throw PatchException("Learning reminders: registration error handler not found")
        val ownerType = failure.instructions.take(failureIndex).last {
            it.opcode == Opcode.IGET_OBJECT
        }.getReference<FieldReference>()!!.definingClass
        val ownerIndex = (0 until failureIndex).last {
            failure.getInstruction(it).let { instruction ->
                instruction.opcode == Opcode.CHECK_CAST &&
                    instruction.getReference<TypeReference>()?.type == ownerType
            }
        }
        val ownerRegister = failure.getInstruction<OneRegisterInstruction>(ownerIndex).registerA
        val registerIndex = home.instructions.indexOfFirst {
            it.getReference<MethodReference>()?.let { ref ->
                ref.definingClass == ownerType && ref.returnType == "V" && ref.parameterTypes.isEmpty()
            } == true
        }
        if (registerIndex < 0) throw PatchException("Learning reminders: registration call not found")
        val repositoryLoad = home.getInstruction<TwoRegisterInstruction>(registerIndex - 1)
        if (repositoryLoad.opcode != Opcode.IGET_OBJECT ||
            repositoryLoad.getReference<FieldReference>()?.type != ownerType
        ) throw PatchException("Learning reminders: unsupported registration call layout")
        val repositoryRegister = repositoryLoad.registerA
        val managerLoad = home.instructions.take(registerIndex).filter {
            it.opcode == Opcode.IGET_OBJECT && it.getReference<FieldReference>()?.type == managerType
        }.unique("home local reminder manager") as TwoRegisterInstruction
        val managerRegister = managerLoad.registerA
        // The home branch no longer needs the availability result or its GoogleApiAvailability object.
        // Keep its logger intact for the original catch handler.
        val availabilityIndex = home.instructions.take(registerIndex).indexOfLast {
            it.getReference<MethodReference>()?.name == "isGooglePlayServicesAvailable"
        }
        if (availabilityIndex < 0) throw PatchException("Learning reminders: availability check not found")
        val availabilityCall = home.getInstruction<com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c>(availabilityIndex)
        val userRegister = home.getInstruction<OneRegisterInstruction>(availabilityIndex + 1).registerA
        val languageRegister = availabilityCall.registerC
        if (setOf(repositoryRegister, managerRegister, userRegister, languageRegister).size != 4 ||
            listOf(repositoryRegister, managerRegister, userRegister, languageRegister).any { it > 15 }
        ) throw PatchException("Learning reminders: unsupported home registers")

        val settings = classDefBy(settingsType)
        val getSettings = classDefBy(USER).methods.filter {
            it.returnType == settingsType && it.parameters.isEmpty()
        }.unique("user reminder settings")
        val display = manager.methods.filter {
            it.parameters.map { p -> p.type }.take(2) == listOf(USER, LANGUAGE)
        }.unique("local reminder display")
        val pushEnabled = display.references<FieldReference>().filter {
            it.definingClass == settingsType && it.type == "Z"
        }.unique("pushEnabled")
        val time = settings.fields.filter { it.type == "I" }.unique("reminder time")
        val language = display.references<FieldReference>().filter {
            it.definingClass == USER && it.type == LANGUAGE
        }.unique("user language")
        val prefs = manager.methods.filter { it.returnType == "Landroid/content/SharedPreferences;" }
            .unique("local reminder preferences")
        val pendingIntent = manager.methods.filter { it.returnType == "Landroid/app/PendingIntent;" }
            .unique("practice PendingIntent")
        val executor = home.references<FieldReference>().filter {
            it.definingClass == managerType && it.type != "Z"
        }.unique("local reminder executor")
        val executorGet = home.references<MethodReference>().filter {
            it.definingClass == executor.type && it.name == "getValue"
        }.unique("executor lazy getter")
        val mapGetter = manager.methods.filter {
            it.returnType.startsWith("L") && it.returnType !in setOf(
                OBJECT, "Landroid/content/SharedPreferences;", "Landroid/app/PendingIntent;",
            ) && it.parameters.isEmpty()
        }.unique("language schedule map getter")
        val mapType = mapGetter.returnType
        val scheduleMap = manager.fields.filter { it.type == mapType }.unique("cached language schedule map")
        val mapClass = classDefBy(mapType)
        val containsLanguage = mapClass.methods.filter {
            it.returnType == "Z" && it.parameters.map { p -> p.type } == listOf(LANGUAGE)
        }.unique("language schedule membership")
        val recordLanguage = mapClass.methods.filter {
            it.returnType == "V" && it.parameters.map { p -> p.type } == listOf(LANGUAGE, "J")
        }.unique("language schedule timestamp writer")
        val context = manager.fields.filter { it.type == "Landroid/content/Context;" }.unique("context")
        val clockNow = display.references<MethodReference>().filter {
            it.returnType == "Ljava/time/Instant;" && it.parameterTypes.isEmpty()
        }.unique("clock")
        val clock = manager.fields.filter { it.type == clockNow.definingClass }.unique("clock field")
        val scheduler = classDefBy { cls -> cls.methods.any { method ->
            method.references<MethodReference>().any { it.toString() == pendingIntent.toString() } &&
                method.references<MethodReference>().any { it.definingClass == "Ljava/util/Calendar;" }
        } }.methods.filter { method ->
            method.references<MethodReference>().any { it.toString() == pendingIntent.toString() }
        }.unique("practice alarm scheduler")
        val calendar = scheduler.references<MethodReference>().filter {
            it.returnType == "Ljava/util/Calendar;" && it.parameterTypes.map { p -> p.toString() } == listOf("Ljava/util/TimeZone;")
        }.unique("local calendar")
        val alarmGetter = scheduler.references<MethodReference>().filter {
            it.returnType == "Landroid/app/AlarmManager;"
        }.unique("alarm provider")
        val alarmProvider = manager.fields.filter { it.type == alarmGetter.definingClass }.unique("alarm provider field")
        val flags = LocalReminderManagerFingerprint.method.references<FieldReference>()
            .filter { it.definingClass == managerType && it.type == "Z" }
        if (flags.size != 2) throw PatchException("Learning reminders: local activation flags changed")

        val mutableManager = mutableClassDefBy(managerType)
        fun helper(name: String, registers: Int, body: String): MutableMethod = ImmutableMethod(
            managerType, name,
            listOf(managerType, USER, LANGUAGE).map { ImmutableMethodParameter(it, null, null) },
            "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null,
            MutableMethodImplementation(registers),
        ).toMutable().apply { addInstructions(0, body.trimIndent()) }.also { mutableManager.methods.add(it) }

        val dispatch = helper("morpheDispatchLearningReminder", 7, """
            if-eqz p0, :done
            iget-object v0, p0, $executor
            invoke-interface {v0}, $executorGet
            move-result-object v0
            check-cast v0, Ljava/util/concurrent/ExecutorService;
            new-instance v1, ${EXTENSION.dropLast(1)}${'$'}Reminder;
            invoke-direct {v1, p0, p1, p2}, ${EXTENSION.dropLast(1)}${'$'}Reminder;-><init>($OBJECT$OBJECT$OBJECT)V
            invoke-interface {v0, v1}, Ljava/util/concurrent/ExecutorService;->execute(Ljava/lang/Runnable;)V
            :done
            return-void
        """)
        val schedule = helper("morpheScheduleLearningReminder", 16, """
            if-eqz p0, :done
            if-eqz p1, :done
            ${getSettings.invoke()} {p1}, $getSettings
            move-result-object v0
            iget-boolean v1, v0, $pushEnabled
            if-eqz v1, :done
            iget v12, v0, $time
            if-ltz v12, :done
            const/16 v1, 1440
            if-ge v12, v1, :done
            if-nez p2, :language_ready
            iget-object p2, p1, $language
            if-nez p2, :language_ready
            sget-object p2, $LANGUAGE->ENGLISH:$LANGUAGE
            :language_ready
            const/4 v1, 0x1
            iput-boolean v1, p0, ${flags[0]}
            iput-boolean v1, p0, ${flags[1]}
            ${prefs.invoke()} {p0}, $prefs
            move-result-object v0
            invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
            move-result-object v0
            const-string v2, "local_notifications_enabled"
            invoke-interface {v0, v2, v1}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String; Z)Landroid/content/SharedPreferences${'$'}Editor;
            const-string v2, "local_notifications_trumps_ab_bucket"
            invoke-interface {v0, v2, v1}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String; Z)Landroid/content/SharedPreferences${'$'}Editor;
            invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
            iget-object v3, p0, $scheduleMap
            if-nez v3, :map_ready
            ${mapGetter.invoke()} {p0}, $mapGetter
            move-result-object v3
            iput-object v3, p0, $scheduleMap
            :map_ready
            iget-object v4, p0, $clock
            invoke-interface {v4}, $clockNow
            move-result-object v5
            invoke-virtual {v5}, Ljava/time/Instant;->toEpochMilli()J
            move-result-wide v5
            ${containsLanguage.invoke()} {v3, p2}, $containsLanguage
            move-result v0
            if-nez v0, :timestamp_ready
            ${recordLanguage.invoke()} {v3, p2, v5, v6}, $recordLanguage
            :timestamp_ready
            const/4 v0, 0x0
            invoke-interface {v4, v0}, $calendar
            move-result-object v7
            const/16 v0, 11
            const/4 v2, 0x0
            invoke-virtual {v7, v0, v2}, Ljava/util/Calendar;->set(I I)V
            const/16 v0, 12
            invoke-virtual {v7, v0, v2}, Ljava/util/Calendar;->set(I I)V
            const/16 v0, 13
            invoke-virtual {v7, v0, v2}, Ljava/util/Calendar;->set(I I)V
            const/16 v0, 14
            invoke-virtual {v7, v0, v2}, Ljava/util/Calendar;->set(I I)V
            invoke-virtual {v7}, Ljava/util/Calendar;->getTimeInMillis()J
            move-result-wide v8
            int-to-long v10, v12
            const-wide/32 v2, 60000
            mul-long/2addr v10, v2
            add-long/2addr v8, v10
            cmp-long v0, v5, v8
            if-ltz v0, :time_ready
            const-wide/32 v2, 86400000
            add-long/2addr v8, v2
            :time_ready
            iget-object v0, p0, $context
            ${pendingIntent.invoke()} {p0, v0, p2}, $pendingIntent
            move-result-object v0
            iget-object v2, p0, $alarmProvider
            invoke-virtual {v2}, $alarmGetter
            move-result-object v2
            invoke-virtual {v2, v1, v8, v9, v0}, Landroid/app/AlarmManager;->set(I J Landroid/app/PendingIntent;)V
            :done
            return-void
        """)

        for ((bridge, target) in listOf("schedule" to dispatch, "runScheduled" to schedule)) {
            mutableClassDefBy(EXTENSION).methods.single { it.name == bridge }.addInstructions(0, """
                check-cast p0, $managerType
                check-cast p1, $USER
                check-cast p2, $LANGUAGE
                invoke-static/range {p0 .. p2}, $target
                return-void
            """.trimIndent())
        }
        // Keep this insertion straight-line. A missing language is normalized in the helper,
        // whose labels are assembled at the start of a new method.
        home.addInstructions(registerIndex, """
            move-object/from16 v$userRegister, p1
            check-cast v$userRegister, $USER
            iget-object v$languageRegister, v$userRegister, $language
            invoke-static {v$repositoryRegister, v$managerRegister, v$userRegister, v$languageRegister}, $EXTENSION->bind($OBJECT$OBJECT$OBJECT$OBJECT)V
        """.trimIndent())
        failure.addInstructions(ownerIndex + 1, """
            invoke-static/range {v$ownerRegister .. v$ownerRegister}, $EXTENSION->onRegistrationFailure($OBJECT)V
        """.trimIndent())
        // Logout can finish while a registration task is still running. Its late failure must not
        // reactivate reminders for the account that just signed out.
        mutableClassDefBy(ownerType).methods.filter {
            it.returnType == "V" && it.parameters.map { p -> p.type } ==
                listOf("Lcom/duolingo/core/data/model/UserId;")
        }.unique("device unregistration").addInstructions(0, """
            invoke-static/range {p0 .. p0}, $EXTENSION->unbind($OBJECT)V
        """.trimIndent())
    }
}
