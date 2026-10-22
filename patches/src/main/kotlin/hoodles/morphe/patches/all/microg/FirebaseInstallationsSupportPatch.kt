package hoodles.morphe.patches.all.microg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import java.util.Locale

private const val FIS_ENDPOINT = "https://firebaseinstallations.googleapis.com/v1/"
private const val CONNECTION = "Ljava/net/URLConnection;"
private const val STRING = "Ljava/lang/String;"
private val identityHeaders = linkedMapOf(
    "X-Android-Package" to "morpheAddFirebasePackageHeader",
    "X-Android-Cert" to "morpheAddFirebaseCertificateHeader",
)
private fun unsupported(detail: String): Nothing = throw PatchException("Firebase Installations identity: $detail")
private fun Method.code() = implementation?.instructions?.toList().orEmpty()
private fun Instruction.string() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
private fun Instruction.method() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Instruction.arguments(): List<Int>? = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> null
}
private fun Instruction.isHeaderWrite(): Boolean = method()?.let {
    it.definingClass in setOf(CONNECTION, "Ljava/net/HttpURLConnection;") &&
        it.name == "addRequestProperty" && it.parameterTypes.map { type -> type.toString() } == listOf(STRING, STRING) &&
        it.returnType == "V"
} == true

/** Resolve keys only across a single entry, straight-line span. Never guess reused registers. */
private fun headerKey(code: List<Instruction>, call: Int, exceptionEntries: Set<Int>): String? {
    val args = code[call].arguments() ?: unsupported("unsupported header invocation encoding")
    if (args.size != 3) unsupported("unexpected header invocation argument count")
    val register = args[1]
    val offsets = code.runningFold(0) { sum, instruction -> sum + instruction.codeUnits }
    val incoming = exceptionEntries.toMutableSet()
    code.forEachIndexed { index, instruction ->
        if (instruction is OffsetInstruction) {
            val target = offsets[index] + instruction.codeOffset
            incoming += target
            if (instruction.opcode == Opcode.PACKED_SWITCH || instruction.opcode == Opcode.SPARSE_SWITCH) {
                val payload = code.getOrNull(offsets.indexOf(target)) as? SwitchPayload
                    ?: unsupported("invalid switch payload")
                incoming += payload.switchElements.map { offsets[index] + it.offset }
            }
        }
    }
    for (index in call - 1 downTo 0) {
        val instruction = code[index]
        if (instruction is OffsetInstruction || !instruction.opcode.canContinue() ||
            offsets[index + 1] in incoming
        ) return null
        if (instruction.opcode.setsRegister() && instruction is OneRegisterInstruction &&
            (instruction.registerA == register ||
                (instruction.opcode.setsWideRegister() && instruction.registerA + 1 == register))
        ) return if (instruction.opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO)) instruction.string() else null
    }
    return null
}

/** FIS alone needs the original API-key identity after the APK is signed by Manager. */
internal val firebaseInstallationsSupportPatch = bytecodePatch {
    dependsOn(microGMetadataPatch)
    execute {
        // Only Duolingo has been validated; preserve other apps' MicroG behavior.
        if (MicroGMetadata.packageName != "com.duolingo") return@execute
        val fisClasses = mutableListOf<ClassDef>()
        classDefForEach { clazz ->
            if (clazz.methods.any { method -> method.code().any {
                it.string()?.contains("firebaseinstallations.googleapis.com") == true
            } }) fisClasses += clazz
        }
        if (fisClasses.isEmpty()) return@execute
        val owner = fisClasses.singleOrNull() ?: unsupported("multiple FIS clients found")
        if (owner.methods.none { method -> method.code().any { it.string() == FIS_ENDPOINT } })
            unsupported("unsupported FIS endpoint")
        val method = owner.methods.filter { method ->
            method.returnType == "Ljava/net/HttpURLConnection;" &&
                method.parameterTypes.map { it.toString() } == listOf("Ljava/net/URL;", STRING) &&
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.code().mapNotNull { it.string() }.containsAll(identityHeaders.keys + "x-goog-api-key")
        }.singleOrNull() ?: unsupported("connection factory is missing or ambiguous")
        val code = method.code()
        val writes = code.indices.filter { code[it].isHeaderWrite() }
        val exceptionEntries = method.implementation!!.tryBlocks.flatMap { block ->
            block.exceptionHandlers.map { it.handlerCodeAddress }
        }.toSet()
        val keys = writes.associateWith { headerKey(code, it, exceptionEntries) }
        val hooks = identityHeaders.map { (header, helper) ->
            val index = keys.filterValues { it == header }.keys.singleOrNull()
                ?: unsupported("$header write is missing or ambiguous")
            val instruction = code[index]
            if (instruction.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE))
                unsupported("unsupported $header invocation")
            Triple(index, helper, header)
        }
        // A header literal with an unresolved write could hide a second identity assignment.
        if (keys.values.any { it == null }) unsupported("unresolved request header flow")
        if (owner.methods.any { it.name in identityHeaders.values }) unsupported("helper name collision")
        val originalPackage = MicroGMetadata.packageName
        if (!Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+").matches(originalPackage))
            unsupported("invalid original package metadata")
        val certificate = MicroGMetadata.signatureSHA1.uppercase(Locale.ROOT)
        if (!Regex("[0-9A-F]{40}").matches(certificate)) unsupported("invalid original certificate metadata")
        val mutableOwner = mutableClassDefBy(owner.type)
        val mutableMethod = mutableOwner.findMutableMethodOf(method)
        hooks.forEach { (index, name, header) ->
            val parameters = listOf(CONNECTION, STRING, STRING)
            val reference = ImmutableMethodReference(owner.type, name, parameters, "V")
            val identity = if (header == "X-Android-Package") originalPackage else certificate
            val helper = ImmutableMethod(
                owner.type, name, parameters.map { ImmutableMethodParameter(it, null, null) }, "V",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
                null, null, MutableMethodImplementation(3),
            ).toMutable().apply {
                addInstructions(0, """
                    const-string p1, "$header"
                    const-string p2, "$identity"
                    invoke-virtual/range {p0 .. p2}, $CONNECTION->addRequestProperty($STRING$STRING)V
                    return-void
                """.trimIndent())
            }
            mutableOwner.methods.add(helper)
            val original = code[index]
            val replacement = when (original) {
                is FiveRegisterInstruction -> BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, original.registerCount, original.registerC, original.registerD,
                    original.registerE, original.registerF, original.registerG, reference,
                )
                is RegisterRangeInstruction -> BuilderInstruction3rc(
                    Opcode.INVOKE_STATIC_RANGE, original.startRegister, original.registerCount, reference,
                )
                else -> unsupported("unsupported header invocation encoding")
            }
            mutableMethod.replaceInstruction(index, replacement)
        }
    }
}
