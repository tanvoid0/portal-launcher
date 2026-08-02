plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Room writes the schema of every version here, and these files are committed.
// They are what makes a migration reviewable and testable: MigrationTestHelper
// builds an old database from them, and Room validates the post-migration schema
// against them. Without the export, a migration can only be verified by hand.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * versionCode from the commit count, versionName from the build script.
 *
 * A hand-edited versionCode is the one number that must never repeat or go backwards,
 * and Play rejects a bundle that gets it wrong — so it is derived rather than typed.
 * Falls back to 1 outside a git checkout (a source archive, a shallow CI clone) so the
 * build still works; only the release job needs the real number, and it fetches
 * full history.
 */
val gitCommitCount: Int by lazy {
    runCatching {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim().toInt()
    }.getOrDefault(1)
}

android {
    namespace = "com.tanvoid0.portallauncher"
    compileSdk = 36

    // When raising targetSdk, use SDK Upgrade Assistant and review behavior changes.
    // Tools > Android SDK Upgrade Assistant (upgrade one API level at a time).
    // https://developer.android.com/build/sdk-upgrade-assistant
    // Android 16 changes: https://developer.android.com/about/versions/16/behavior-changes-16
    defaultConfig {
        applicationId = "com.tanvoid0.portallauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * Release signing from the environment, never from a file in the repository.
     *
     * Absent credentials produce an *unsigned* release build rather than a failure, so
     * `assembleRelease` still exercises R8 on a developer machine and in PR CI. Only the
     * tagged release job has the secrets, and it is the only place that needs them.
     */
    val keystoreFile = System.getenv("PORTAL_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("PORTAL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PORTAL_KEY_ALIAS")
                keyPassword = System.getenv("PORTAL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Separate package so a debug build can be installed alongside the
            // release one — you keep a working home screen while testing.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MigrationTestHelper reads the exported schemas as instrumentation assets.
    sourceSets.getByName("androidTest").assets.srcDirs("$projectDir/schemas")

    lint {
        warningsAsErrors = true
        abortOnError = true
        // No baseline: lint currently passes clean, and an empty baseline is only a
        // place for debt to accumulate silently. If real deferred debt ever appears,
        // add `baseline = file("lint-baseline.xml")` and ./gradlew updateLintBaseline.
        // Raising targetSdk is its own reviewed task (SDK Upgrade Assistant, one
        // API level at a time, then re-test overlay/notification/home behaviour).
        // It must not happen as a side effect of turning lint gating on.
        disable += "OldTargetApi"
        // Dependency-freshness checks hit the network and fire whenever anything
        // upstream publishes, so with warningsAsErrors they break the build for
        // reasons that have nothing to do with the commit. Updating deps is a
        // scheduled job, not a build gate.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.genai.prompt)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(bom)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}