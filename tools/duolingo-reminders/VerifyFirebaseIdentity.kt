/*
 * Compile against Manager's patcher-host.jar and run:
 * VerifyFirebaseIdentityKt <original.apk> <patched-dex-directory>
 * This independently reads the APK identity and checks only the FIS client's
 * two header hooks, preserving every original instruction, branch and try range.
 * It does not test Firebase's server response or Android runtime behavior.
 */
import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.io.File
import java.security.MessageDigest

private val headers = linkedMapOf(
    "morpheAddFirebasePackageHeader" to "X-Android-Package",
    "morpheAddFirebaseCertificateHeader" to "X-Android-Cert",
)
private const val CONNECTION = "Ljava/net/URLConnection;"
private const val STRING = "Ljava/lang/String;"
private fun Method.code() = implementation?.instructions?.toList().orEmpty()
private fun Method.key() = name + "(" + parameterTypes.joinToString("") + ")" + returnType
private fun Instruction.ref() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Instruction.text() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
private fun Instruction.args(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> error("Not an invocation")
}
private fun Instruction.operands(): List<Any> = buildList {
    add(opcode); add(codeUnits)
    if (this@operands is OneRegisterInstruction) add(registerA)
    if (this@operands is TwoRegisterInstruction) add(registerB)
    if (this@operands is ThreeRegisterInstruction) add(registerC)
    if (this@operands is FiveRegisterInstruction || this@operands is RegisterRangeInstruction) add(args())
    if (this@operands is ReferenceInstruction) add(reference.toString())
    if (this@operands is DualReferenceInstruction) add(reference2.toString())
    if (this@operands is WideLiteralInstruction) add(wideLiteral)
    if (this@operands is OffsetInstruction) add(codeOffset)
    if (this@operands is VerificationErrorInstruction) add(verificationError)
    if (this@operands is FieldOffsetInstruction) add(fieldOffset)
    if (this@operands is InlineIndexInstruction) add(inlineIndex)
    if (this@operands is VtableIndexInstruction) add(vtableIndex)
    if (this@operands is ArrayPayload) { add(elementWidth); add(arrayElements.toList()) }
    if (this@operands is SwitchPayload) add(switchElements.map { it.key to it.offset })
}
private fun Method.tries() = implementation?.tryBlocks?.map { block ->
    Triple(block.startCodeAddress, block.codeUnitCount,
        block.exceptionHandlers.map { it.exceptionType to it.handlerCodeAddress })
}.orEmpty()
private fun classes(file: File) = DexFileFactory.loadDexContainer(file, Opcodes.getDefault()).let { container ->
    container.dexEntryNames.flatMap { container.getEntry(it)!!.dexFile.classes }
}

// Run with --planner-tests and the compiled patch bundle on the JVM classpath.
private fun plannerTests() {
    val owner = Class.forName("hoodles.morphe.patches.all.microg.FirebaseInstallationsSupportPatchKt")
    val key = owner.getDeclaredMethod("headerKey", List::class.java, Int::class.javaPrimitiveType, Set::class.java).apply { isAccessible = true }
    val header = com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference("X-Android-Cert")
    val write = com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference(
        CONNECTION, "addRequestProperty", listOf(STRING, STRING), "V")
    val constant = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c(Opcode.CONST_STRING, 1, header)
    val jumbo = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 1, header)
    val call = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 3, 0, 1, 2, 0, 0, write)
    val range = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 3, write)
    val overwrite = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n(Opcode.CONST_4, 1, 0)
    fun test(name: String, instructions: List<Instruction>, expected: String?) {
        check(key.invoke(null, instructions, instructions.lastIndex, emptySet<Int>()) == expected) { "Header planner regression: $name" }
        println("PASS planner $name")
    }
    test("ordinary invocation", listOf(constant, call), "X-Android-Cert")
    test("range invocation", listOf(constant, range), "X-Android-Cert")
    test("jumbo string", listOf(jumbo, call), "X-Android-Cert")
    test("unknown key", listOf(call), null)
    test("reused key register", listOf(constant, overwrite, call), null)
    val jumpToCall = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10t(Opcode.GOTO, 3)
    test("incoming branch skips constant", listOf(jumpToCall, constant, call), null)
    check(key.invoke(null, listOf(constant, call), 1, setOf(2)) == null)
    println("PASS planner exception handler skips constant")
    val jumpOverSpan = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10t(Opcode.GOTO, 1)
    test("branch inside constant span", listOf(constant, jumpOverSpan, call), null)
    val malformed = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 0, 1, 0, 0, 0, write)
    val failure = runCatching { key.invoke(null, listOf(constant, malformed), 1, emptySet<Int>()) }.exceptionOrNull()
    check((failure as? java.lang.reflect.InvocationTargetException)?.cause is app.morphe.patcher.patch.PatchException)
    println("PASS planner malformed argument count rejected")
}
fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--planner-tests"))) { plannerTests(); return }
    require(args.size == 2) { "Expected original APK and patched DEX directory" }
    val originalApk = File(args[0])
    val verification = ApkVerifier.Builder(originalApk).build().verify()
    check(verification.isVerified) { "Original APK signature verification failed" }
    val signer = verification.signerCertificates.single()
    val originalCertificate = MessageDigest.getInstance("SHA-1").digest(signer.encoded)
        .joinToString("") { "%02X".format(it) }
    val originalPackage = ApkModule.loadApkFile(originalApk).androidManifest.packageName
    val originalOwner = classes(originalApk).filter { clazz -> clazz.methods.any { method ->
        method.code().any { it.text() == "https://firebaseinstallations.googleapis.com/v1/" }
    } }.single()
    val dexFiles = File(args[1]).listFiles()!!.filter { it.extension == "dex" }
    val patchedOwner = dexFiles.flatMap { classes(it) }.single { it.type == originalOwner.type }
    check(patchedOwner.fields.map { it.toString() }.toSet() == originalOwner.fields.map { it.toString() }.toSet())
    check(patchedOwner.methods.count() == originalOwner.methods.count() + 2) { "Unexpected FIS methods added or removed" }
    val helperTypes = listOf(CONNECTION, STRING, STRING)
    headers.forEach { (name, header) ->
        val helper = patchedOwner.methods.single { it.name == name }
        check(helper.parameterTypes.map { it.toString() } == helperTypes && helper.returnType == "V")
        check(helper.accessFlags == AccessFlags.PRIVATE.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value)
        check(helper.implementation!!.registerCount == 3 && helper.tries().isEmpty())
        val code = helper.code()
        check(code.size == 4)
        check(code[0].opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
            (code[0] as OneRegisterInstruction).registerA == 1 && code[0].text() == header)
        check(code[1].opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
            (code[1] as OneRegisterInstruction).registerA == 2 &&
            code[1].text() == if (header == "X-Android-Package") originalPackage else originalCertificate)
        check(code[2].opcode == Opcode.INVOKE_VIRTUAL_RANGE && code[2].args() == listOf(0, 1, 2))
        check(code[2].ref().toString() == "$CONNECTION->addRequestProperty($STRING$STRING)V")
        check(code[3].opcode == Opcode.RETURN_VOID)
        println("PASS helper $name: $header uses verified original identity")
    }
    val seen = mutableListOf<String>()
    originalOwner.methods.forEach { original ->
        val patched = patchedOwner.methods.single { it.key() == original.key() }
        check(original.accessFlags == patched.accessFlags)
        check(original.implementation?.registerCount == patched.implementation?.registerCount)
        check(original.tries() == patched.tries()) { "Original try ranges changed in ${original.key()}" }
        val before = original.code()
        val after = patched.code()
        check(before.size == after.size) { "Instructions inserted/removed in ${original.key()}" }
        before.indices.forEach { index ->
            val old = before[index]
            val fresh = after[index]
            val hook = fresh.ref()
            if (hook?.definingClass == originalOwner.type && hook.name in headers) {
                check(old.ref()?.definingClass in setOf(CONNECTION, "Ljava/net/HttpURLConnection;"))
                check(old.ref()?.name == "addRequestProperty" && old.ref()?.returnType == "V" &&
                    old.ref()?.parameterTypes?.map { it.toString() } == listOf(STRING, STRING))
                check(old.opcode == Opcode.INVOKE_VIRTUAL && fresh.opcode == Opcode.INVOKE_STATIC ||
                    old.opcode == Opcode.INVOKE_VIRTUAL_RANGE && fresh.opcode == Opcode.INVOKE_STATIC_RANGE)
                check(old.codeUnits == fresh.codeUnits && old.args() == fresh.args())
                check(hook.parameterTypes.map { it.toString() } == helperTypes && hook.returnType == "V")
                val keyRegister = old.args()[1]
                val keyDefinition = before.take(index).last { instruction ->
                    instruction.opcode.setsRegister() && instruction is OneRegisterInstruction &&
                        (instruction.registerA == keyRegister ||
                            instruction.opcode.setsWideRegister() && instruction.registerA + 1 == keyRegister)
                }
                check(keyDefinition.text() == headers.getValue(hook.name)) { "Hook replaced the wrong header" }
                seen += hook.name
            } else check(old.operands() == fresh.operands()) {
                "Original operand/branch changed in ${original.key()} at instruction $index"
            }
        }
    }
    check(seen.sorted() == headers.keys.sorted()) { "Expected exactly one invocation of each FIS helper: $seen" }
    println("PASS FIS ${originalOwner.type}: exactly two header invokes changed; all original registers, operands, branches and try ranges preserved")
    println("PASS original package $originalPackage, certificate SHA-1 $originalCertificate")
}
