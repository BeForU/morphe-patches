import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.dex.BytecodeMode
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect

fun main(args: Array<String>) = runBlocking {
    val patches = PatchBundleLoader.loadFlat(listOf(File(args[0])))
    check(patches.size == 1)
    val patch = patches.single()
    check(patch.name == "Hide swipe up guide" && !patch.default)
    val compat = patch.compatibility!!.single()
    check(compat.packageName == "com.instagram.android")
    check(compat.targets.single().version == "452.1.0.53.64")
    check(PatchBundleLoader.extractVersion(File(args[0])) == "1.0.0")
    println("BUNDLE: metadata, target version and opt-in selection OK")
    Patcher(PatcherConfig(File(args[1]), temporaryFilesPath = File(args[2]),
        useBytecodeMode = BytecodeMode.STRIP_SAFE)).use { patcher ->
        patcher += patches.toSet()
        patcher().collect { result ->
            result.exception?.let { throw it }
            println("PATCH: ${result.patch.name}: OK")
        }
        val output = File(args[3]).apply { mkdirs() }
        patcher.get().dexFiles.forEach { dex ->
            dex.stream.use { input -> output.resolve(dex.name).outputStream().use { input.copyTo(it) } }
        }
        println("DEX output: ${output.absolutePath}")
    }
}
