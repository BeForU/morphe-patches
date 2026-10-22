android {
    defaultConfig {
        minSdk = 26
    }
}

// These tests exercise asynchronous registration state without an Android runtime.
val compileFallbackCoordinatorTests = tasks.register<JavaCompile>("compileFallbackCoordinatorTests") {
    source(
        "src/main/java/hoodles/morphe/extension/duolingo/FallbackCoordinator.java",
        "src/test/java/hoodles/morphe/extension/duolingo/FallbackCoordinatorTest.java",
    )
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("fallback-coordinator-tests"))
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
}

val testFallbackCoordinator = tasks.register<JavaExec>("testFallbackCoordinator") {
    dependsOn(compileFallbackCoordinatorTests)
    classpath = files(compileFallbackCoordinatorTests.flatMap { it.destinationDirectory })
    mainClass.set("hoodles.morphe.extension.duolingo.FallbackCoordinatorTest")
}

tasks.named("check") {
    dependsOn(testFallbackCoordinator)
}
