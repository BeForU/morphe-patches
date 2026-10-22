/*
 * Compile against Morphe's patcher-host.jar (which includes dexlib2), then run
 * VerifyReminderBytecodeKt <original-base.apk> <patched-dex-output-directory>.
 * This checks the home hook's register lifetime and preservation of the original
 * method. It is a targeted regression audit, not a complete Android verifier.
 * The formerly broken bundle must fail because it has no early binding helper.
 */
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.io.File

private const val USER_TYPE = "Lcom/duolingo/data/user/User;"
private const val LANGUAGE_TYPE = "Lcom/duolingo/core/language/Language;"
private const val BIND_HELPER = "morpheBindLearningReminder"
private const val FALLBACK = "Lhoodles/morphe/extension/duolingo/LearningReminderFallback;"
private fun Method.code() = implementation!!.instructions.toList()
private fun Instruction.methodRef() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun MethodReference.signature() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
private fun Instruction.registerArguments(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> error("Expected invocation: $opcode")
}

// Compare every encoded operand except offsets, which need address normalization.
private fun Instruction.operands(): List<Any> = buildList {
    add(opcode)
    if (this@operands is OneRegisterInstruction) add(registerA)
    if (this@operands is TwoRegisterInstruction) add(registerB)
    if (this@operands is ThreeRegisterInstruction) add(registerC)
    if (this@operands is FiveRegisterInstruction || this@operands is RegisterRangeInstruction) add(registerArguments())
    if (this@operands is ReferenceInstruction) add(reference.toString())
    if (this@operands is DualReferenceInstruction) add(reference2.toString())
    if (this@operands is WideLiteralInstruction) add(wideLiteral)
    if (this@operands is VerificationErrorInstruction) add(verificationError)
    if (this@operands is FieldOffsetInstruction) add(fieldOffset)
    if (this@operands is InlineIndexInstruction) add(inlineIndex)
    if (this@operands is VtableIndexInstruction) add(vtableIndex)
    if (this@operands is ArrayPayload) { add(elementWidth); add(arrayElements.toList()) }
    if (this@operands is SwitchPayload) add(switchElements.map { it.key })
}

private fun verifyPreserved(before: Method, after: Method, hook: Int) {
    check(before.implementation!!.registerCount == after.implementation!!.registerCount) {
        "Original home register count changed"
    }
    val old = before.code()
    val fresh = after.code()
    // DEX writers may add/remove an alignment NOP before a payload.
    val oldIndices = old.indices.filter { old[it].opcode != Opcode.NOP }
    val newIndices = fresh.indices.filter { it != hook && fresh[it].opcode != Opcode.NOP }
    check(oldIndices.size == newIndices.size) { "Home must contain exactly one new hook instruction" }
    val oldOffsets = old.runningFold(0) { sum, ins -> sum + ins.codeUnits }
    val newOffsets = fresh.runningFold(0) { sum, ins -> sum + ins.codeUnits }
    fun targetPosition(offsets: List<Int>, kept: List<Int>, address: Int): Int {
        val index = offsets.indexOf(address)
        check(index >= 0) { "Target is not an instruction boundary: $address" }
        return kept.indexOfFirst { it >= index }.let { if (it < 0) kept.size else it }
    }
    for (position in oldIndices.indices) {
        val oi = oldIndices[position]
        val ni = newIndices[position]
        val o = old[oi]
        val n = fresh[ni]
        check(o.operands() == n.operands()) { "Original operands changed at instruction $oi: ${o.opcode}" }
        if (o is OffsetInstruction) {
            check(n is OffsetInstruction)
            val oldTarget = targetPosition(oldOffsets, oldIndices, oldOffsets[oi] + o.codeOffset)
            val newTarget = targetPosition(newOffsets, newIndices, newOffsets[ni] + n.codeOffset)
            check(oldTarget == newTarget) { "Original branch target changed at instruction $oi" }
            check(newOffsets[ni] + n.codeOffset != newOffsets[hook]) {
                "A branch enters the hook without passing the original manager load"
            }
        }
        if (o is SwitchPayload) {
            check(n is SwitchPayload)
            val oldSwitch = old.indices.single { i -> old[i].opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH) &&
                oldOffsets[i] + (old[i] as OffsetInstruction).codeOffset == oldOffsets[oi] }
            val newSwitch = fresh.indices.single { i -> fresh[i].opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH) &&
                newOffsets[i] + (fresh[i] as OffsetInstruction).codeOffset == newOffsets[ni] }
            for ((a, b) in o.switchElements.zip(n.switchElements)) {
                check(targetPosition(oldOffsets, oldIndices, oldOffsets[oldSwitch] + a.offset) ==
                    targetPosition(newOffsets, newIndices, newOffsets[newSwitch] + b.offset)) {
                    "Switch case ${a.key} changed its destination"
                }
                check(newOffsets[newSwitch] + b.offset != newOffsets[hook]) { "Switch case enters the hook directly" }
            }
        }
    }
    fun tries(method: Method, offsets: List<Int>, kept: List<Int>) = method.implementation!!.tryBlocks.map { block ->
        listOf(targetPosition(offsets, kept, block.startCodeAddress),
            targetPosition(offsets, kept, block.startCodeAddress + block.codeUnitCount),
            block.exceptionHandlers.map { handler ->
                handler.exceptionType to targetPosition(offsets, kept, handler.handlerCodeAddress)
            })
    }
    check(tries(before, oldOffsets, oldIndices) == tries(after, newOffsets, newIndices)) {
        "Original exception handler coverage or targets changed"
    }
    println("PASS: Home register count, original operands, branch/switch targets, and exception handlers preserved")
}

fun main(args: Array<String>) {
    require(args.size == 2) { "Expected <original-base.apk> <patched-dex-output-directory>" }
    val container = DexFileFactory.loadDexContainer(File(args[0]), Opcodes.getDefault())
    val original = container.dexEntryNames.flatMap { container.getEntry(it)!!.dexFile.classes }.associateBy { it.type }
    val dexFiles = File(args[1]).listFiles()?.filter { it.extension == "dex" }.orEmpty()
    check(dexFiles.isNotEmpty()) { "No patched DEX files found" }
    val classes = dexFiles.flatMap { DexFileFactory.loadDexFile(it, Opcodes.getDefault()).classes }
    check(classes.map { it.type }.distinct().size == classes.size) { "Duplicate patched class definitions" }
    val methods = classes.flatMap { it.methods }
    val helper = methods.singleOrNull { it.name == BIND_HELPER }
        ?: error("Missing unique early binding helper; old unsafe p1 binding is rejected")
    val hookSites = methods.flatMap { method -> method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
        if (instruction.methodRef()?.signature() == helper.signature()) method to index else null
    }.orEmpty() }
    check(hookSites.size == 1) { "Expected exactly one home binding hook" }
    val (home, hook) = hookSites.single()
    val instructions = home.code()
    check(hook > 0 && instructions[hook].opcode == Opcode.INVOKE_STATIC) { "Expected a single static home hook" }
    val load = instructions[hook - 1]
    check(load.opcode == Opcode.IGET_OBJECT && load is TwoRegisterInstruction) { "Hook must follow the original manager load immediately" }
    val managerField = (load as ReferenceInstruction).reference as FieldReference
    val userCast = (0 until hook - 1).lastOrNull { index ->
        instructions[index].opcode == Opcode.CHECK_CAST &&
            ((instructions[index] as? ReferenceInstruction)?.reference as? TypeReference)?.type == USER_TYPE
    } ?: error("No original User cast before the manager load")
    val userRegister = (instructions[userCast] as OneRegisterInstruction).registerA
    for (index in userCast + 1 until hook) {
        val ins = instructions[index]
        if (ins.opcode.setsRegister()) {
            val destination = (ins as OneRegisterInstruction).registerA
            check(destination != userRegister && !(ins.opcode.setsWideRegister() && destination + 1 == userRegister)) {
                "Original User register v$userRegister overwritten before binding at instruction $index"
            }
        }
    }
    val expectedArguments = listOf(load.registerB, load.registerA, userRegister)
    check(expectedArguments.distinct().size == 3) { "Owner, manager, and User registers overlap" }
    check(instructions[hook].registerArguments() == expectedArguments) { "Hook does not pass the live original owner, manager, and User" }
    check(helper.parameterTypes.map { it.toString() } == listOf(managerField.definingClass, managerField.type, USER_TYPE)) {
        "Binding helper parameter types do not match the home hook"
    }
    check(helper.definingClass == managerField.definingClass && helper.returnType == "V" &&
        AccessFlags.PUBLIC.isSet(helper.accessFlags) && AccessFlags.STATIC.isSet(helper.accessFlags)) { "Unexpected helper owner/signature/access" }
    check(helper.implementation!!.registerCount == 5) { "Binding helper must have its own two local registers" }
    val body = helper.code()
    check(body.size == 4 && body[0].opcode == Opcode.IGET_OBJECT && body[1].opcode == Opcode.IGET_OBJECT &&
        body[2].opcode == Opcode.INVOKE_STATIC && body[3].opcode == Opcode.RETURN_VOID) { "Unexpected binding helper body" }
    val repositoryLoad = body[0] as TwoRegisterInstruction
    val repositoryField = (body[0] as ReferenceInstruction).reference as FieldReference
    check(repositoryLoad.registerA == 0 && repositoryLoad.registerB == 2 && repositoryField.definingClass == helper.definingClass)
    val languageLoad = body[1] as TwoRegisterInstruction
    val languageField = (body[1] as ReferenceInstruction).reference as FieldReference
    check(languageLoad.registerA == 1 && languageLoad.registerB == 4 &&
        languageField.definingClass == USER_TYPE && languageField.type == LANGUAGE_TYPE)
    check(body[2].methodRef()?.signature() == "$FALLBACK->bind(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V" &&
        body[2].registerArguments() == listOf(0, 3, 4, 1)) { "Helper does not bind the repository, manager, User, and language in order" }
    check(methods.flatMap { it.implementation?.instructions?.toList().orEmpty() }.count {
        it.methodRef()?.let { ref -> ref.definingClass == FALLBACK && ref.name == "bind" } == true
    } == 1) { "Unexpected additional direct fallback bindings" }
    val before = original.getValue(home.definingClass).methods.single { it.signature() == home.signature() }
    verifyPreserved(before, home, hook)
    val originalRepositoryField = before.code().mapNotNull { (it as? ReferenceInstruction)?.reference as? FieldReference }
    check(originalRepositoryField.any { it.toString() == repositoryField.toString() }) { "Helper references a different repository field" }
    println("PASS: User v$userRegister reaches the early binding hook without being overwritten")
    println("PASS: Binding helper reads repository and language using its own local registers")
    println("AUDITED: ${home.signature()} registers=${home.implementation!!.registerCount}, hook=$hook")
}
