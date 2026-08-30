import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Plain Kotlin: no Android, and none of the library's native half.
//
// Everything this application knows about coffee places lives here -- the domain types, the
// aggregation pipelines, the seed reader and the screen state -- querying MongoDB through
// embedded-mongodb-core, which is the library's Android-free half. Every collection and query in
// it is built on a CommandRunner, so `./gradlew :data:test` runs the whole data layer against a
// scripted one: no SDK, no emulator and no compiled engine.
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
    // In this module's public API: a repository is built from a MongoCollection, and it hands
    // back the command it ran. `bson` and the coroutines come with it.
    api(libs.embedded.mongodb.core)

    // Named as well, because Document is in this module's own signatures and Flow is how seeding
    // reports progress -- a module that uses a type should not be relying on someone else's
    // dependency to supply it.
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
