/*
 * Run the merged bundle through the same JVM patcher shipped by Morphe Manager 2.0.0.
 * Extract assets/jvm/patcher-host.jar.xz from the official Manager APK with XZ/LZMA.
 * Compile this file with the Kotlin JVM compiler against patcher-host.jar.
 * Run ValidateKt with patcher-host.jar, the Manager APK (framework resources), and
 * the compiled validator JAR on the Java classpath, then these arguments:
 *   <bundle.mpp> <original-base.apk> <temporary-work-dir> <output-dir> [SUPER|MAX]
 *   [expected-bundle-version=1.49.0] [expected-app-version=6.102.4]
 * The temporary work directory is replaced by Patcher; choose an expendable path.
 * Output contains the public bundle metadata snapshot and serialized patched DEX.
 * The resource APK remains under <temporary-work-dir>/patched/resources.apk.
 * This validates patch application and serialization, not Android runtime behavior.
 */
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.patch.loadPatchesFromJar
import java.io.File
import java.util.jar.JarFile
import kotlinx.coroutines.runBlocking


fun json(v: Any?): String = when(v) {
    null -> "null"
    is String -> "\""+v.replace("\\", "\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\""
    is Boolean, is Number -> v.toString()
    is Map<*,*> -> v.entries.joinToString(",", "{", "}") { json(it.key.toString())+":"+json(it.value) }
    is Iterable<*> -> v.joinToString(",", "[", "]") { json(it) }
    else -> json(v.toString())
}
fun main(args: Array<String>) = runBlocking {
    require(args.size >= 4) { "Expected bundle, original APK, temporary work directory, and output directory" }
    val expectedBundleVersion = args.getOrElse(5) { "1.49.0" }
    val expectedAppVersion = args.getOrElse(6) { "6.102.4" }
    val patchJar = File(args[0])
    val patches = loadPatchesFromJar(setOf(patchJar)).toSet()
    println("ENGINE: "+Patcher::class.java.getResourceAsStream("version.properties")!!.bufferedReader().use { it.readText() })
    println("ENGINE CLASS: "+Patcher::class.java.protectionDomain.codeSource.location)
    val manifest = JarFile(patchJar).use { it.manifest.mainAttributes }
    check(manifest.getValue("Version") == expectedBundleVersion)
    println("BUNDLE LOADED: ${manifest.getValue("Name")}, ${manifest.getValue("Version")}, ${patches.size} public patches")
    val snapshot = patches.sortedBy {it.name}.map { p -> linkedMapOf(
        "name" to p.name, "description" to p.description, "default" to p.default,
        "dependencies" to p.dependencies.map {it.name ?: it.javaClass.simpleName},
        "compatibility" to p.compatibility?.map { c -> linkedMapOf("packageName" to c.packageName, "name" to c.name, "targets" to c.targets.map {it.version}) },
        "options" to p.options.values.map { o -> linkedMapOf("key" to o.key, "default" to o.default, "values" to o.values, "required" to o.required) }
    ) }
    File(args[3]).mkdirs()
    File(args[3], "bundle-metadata.json").writeText(json(snapshot))
    val duo = patches.filter {p -> p.compatibility?.any { it.packageName=="com.duolingo"} == true }
    val expectedDuolingoPatches = setOf("Enable Premium", "Enable debug mode", "Disable dynamic app icon", "Unlimited RampUp time", "Restore learning reminders")
    check(duo.map { it.name }.toSet() == expectedDuolingoPatches) { "Unexpected Duolingo patches: ${duo.map{it.name}}" }
    duo.forEach {p -> check(p.compatibility!!.single {it.packageName=="com.duolingo"}.targets.map{it.version}==listOf(expectedAppVersion))}
    val microg = patches.single {it.name=="MicroG integration"}
    check(microg.compatibility.isNullOrEmpty()) { "MicroG integration must remain universal" }
    val selected = duo.toMutableSet().apply { add(microg) }
    val variant = args.getOrElse(4){"SUPER"}
    selected.single {it.name=="Enable Premium"}.options.set("premiumVariant", variant)
    println("SELECTION: ${selected.map{it.name}}, premiumVariant=$variant")
    Patcher(PatcherConfig(File(args[1]), temporaryFilesPath = File(args[2]), useBytecodeMode = BytecodeMode.STRIP_SAFE)).use { patcher ->
        patcher += selected
        patcher().collect { result ->
            println("PATCH: ${result.patch.name ?: result.patch.javaClass.simpleName}: ${result.exception ?: "OK"}")
            result.exception?.let { throw it }
        }
        val result=patcher.get()
        result.dexFiles.forEach { dex ->
            val target = File(args[3],dex.name)
            dex.stream.use { input -> target.outputStream().use { input.copyTo(it) } }
            println("DEX: ${target.name} ${target.length()}")
        }
        println("RESOURCES: ${result.resources}")
    }
    println("PASS: All five Duolingo patches and MicroG integration applied together on patcher2.0.0; $variant")
}
