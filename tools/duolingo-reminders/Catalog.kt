import app.morphe.engine.patches.PatchBundleLoader
import java.io.File
import java.util.jar.JarFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Export Manager's public patch metadata from the finished bundle itself. */
private fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Enum<*> -> JsonPrimitive(value.name)
    is Map<*, *> -> JsonObject(value.entries.associate { (key, entry) -> key.toString() to json(entry) })
    is Iterable<*> -> JsonArray(value.map(::json))
    else -> if (value.javaClass.isArray) {
        JsonArray((0 until java.lang.reflect.Array.getLength(value)).map {
            json(java.lang.reflect.Array.get(value, it))
        })
    } else {
        error("Unsupported catalog value type: ${value.javaClass.name}")
    }
}

fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: CatalogKt BUNDLE OUTPUT EXPECTED_VERSION" }
    val bundle = File(args[0])
    val version = PatchBundleLoader.extractVersion(bundle)
    check(version == args[2]) { "Expected bundle version ${args[2]}, found $version" }
    JarFile(bundle).use {
        check(it.manifest.mainAttributes.getValue("Name") == "Hoodles Morphe Patches (Modded)") {
            "The bundle must retain its modded display name"
        }
    }
    val patches = PatchBundleLoader.loadFlat(listOf(bundle)).sortedWith(
        compareBy({ it.name ?: "" }, { it.compatibility?.joinToString { c -> c.packageName ?: "" } ?: "" })
    )
    check(patches.isNotEmpty()) { "No public patches loaded from $bundle" }
    check(patches.count { it.name == "Restore learning reminders" } == 1) {
        "The bundle must contain exactly one local reminder patch"
    }
    val catalog = linkedMapOf(
        "NOTE" to "Generated from all ${patches.size} public patches loaded from the distributed bundle.",
        "version" to version,
        "patches" to patches.map { patch ->
            linkedMapOf(
                "name" to patch.name,
                "description" to patch.description,
                "default" to patch.default,
                "category" to patch.category,
                "dependencies" to patch.dependencies.map { it.name ?: it.javaClass.simpleName },
                "compatiblePackages" to patch.compatibility?.map { compatibility ->
                    linkedMapOf(
                        "packageName" to compatibility.packageName,
                        "name" to compatibility.name,
                        "description" to compatibility.description,
                        "apkFileType" to compatibility.apkFileType?.name,
                        "appIconColor" to compatibility.appIconColor?.let { "#%06X".format(it and 0xFFFFFF) },
                        "signatures" to compatibility.signatures,
                        "targets" to compatibility.targets.map { target ->
                            linkedMapOf(
                                "version" to target.version,
                                "versionCodes" to target.versionCodes?.mapKeys { it.key.name },
                                "isExperimental" to target.isExperimental,
                                "minSdk" to target.minSdk,
                                "description" to target.description
                            )
                        }
                    )
                },
                "options" to patch.options.values.map { option ->
                    linkedMapOf(
                        "key" to option.key,
                        "title" to option.title,
                        "description" to option.description,
                        "required" to option.required,
                        "type" to option.type.toString(),
                        "default" to option.default,
                        "values" to option.values
                    )
                }
            )
        }
    )
    val output = File(args[1])
    output.parentFile?.mkdirs()
    val encoder = Json { prettyPrint = true }
    output.writeText(encoder.encodeToString(JsonElement.serializer(), json(catalog)) + "\n", Charsets.UTF_8)
    println("Catalog: ${patches.size} public patches -> $output")
}
