import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.jeroenvervaeke.coffeefinder"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jeroenvervaeke.coffeefinder"

        // The library's own floor is 24, and that is the API level its prebuilt engine libraries
        // are compiled against -- but it is a compile-time floor that has not been run on a
        // device below 9.0, and the engine is known to crash there. 28 is where this application
        // is prepared to claim it works. See `minSdk 24 is the floor` in the library's
        // android/README.md; lowering this belongs with a device that proves it.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // The AAR's own abiFilters do not reach the application's. Named here so that no other
        // native code can produce a 32-bit split -- one that would install and then fail to load
        // an engine, because MongoDB has no 32-bit build.
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            // The library ships consumer-rules.pro, which keeps the JNI entry points and the BSON
            // codecs R8 would otherwise rename or strip.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        named("main") { java.srcDirs("src/main/kotlin") }
        named("test") { java.srcDirs("src/test/kotlin") }
    }

    packaging {
        resources {
            // Both AAR and JAR dependencies carry these, and two copies of a licence is a
            // packaging failure rather than a legal improvement. The texts this application is
            // obliged to ship are in assets/places/licenses and are shown on the About screen.
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
    }

    androidResources {
        // The seed is already deflated; letting aapt2 compress it again costs build time and
        // gains nothing, and it has to be a stream at run time either way. The extension is
        // `gzip` rather than `gz` deliberately -- see SEED_ASSET.
        noCompress += "gzip"
    }

    lint {
        warningsAsErrors = true
        disable += setOf(
            // Both ask a remote repository what was published since, so someone else's release
            // becomes a failing build on a machine that changed nothing.
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
            "GradleDependency",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

// The virtual-time controls in kotlinx-coroutines-test are still marked experimental, and the
// engine lifecycle tests step over cancellation with them. Opted in for the tests only, so
// production code still has to say so at the call site.
tasks.matching { it.name.endsWith("UnitTestKotlin") }.configureEach {
    (this as KotlinCompile).compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

// ShippedAssetsTest reads what the asset merger produced rather than what the source tree holds,
// because the merger rewrites some assets on the way through and only its output shows it.
// Matched by name rather than named directly: AGP registers the unit test tasks later than this.
tasks.matching { it.name.endsWith("UnitTest") }.configureEach { dependsOn("mergeDebugAssets") }

dependencies {
    implementation(project(":data"))
    implementation(libs.embedded.mongodb)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)

    // Location only. The rest of Play services is not on the classpath, and nothing in this
    // application talks to a network.
    implementation(libs.play.services.location)
}
