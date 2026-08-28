import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Plain Kotlin, no Android and no embedded-mongodb.
//
// Everything this application knows about coffee places lives here -- the domain types, the
// aggregation pipelines, the seed reader and the screen state -- reaching MongoDB only through
// the MongoSeam interface. That is what lets the whole data layer be tested with
// `./gradlew :data:test`, with no SDK, no emulator and no compiled engine.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    // 17 rather than whichever JDK Gradle is running on: `:app` compiles at 17, because that is
    // what AGP and the library are built against, and a module it depends on cannot be newer.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    // Both are in this module's public API: Document is every query and every reply, and Flow is
    // how seeding reports progress and how the finders publish state.
    api(libs.bson)
    api(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}

// The virtual-time controls in kotlinx-coroutines-test are still marked experimental, and every
// test that has to step over a debounce uses them. Opted in here rather than at each of them,
// and for the tests only, so production code still has to say so at the call site.
tasks.named<KotlinCompile>("compileTestKotlin") {
    compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

tasks.test { useJUnit() }
