import java.util.Properties

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

dependencies {
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    "testDebugImplementation"(libs.roborazzi)
    "testDebugImplementation"(libs.roborazzi.compose)
    "testDebugImplementation"(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

val secrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.isFile) {
        secretsFile.inputStream().use { load(it) }
    }
}

fun String.asJavaStringLiteral(): String = "\"" + buildString {
    for (char in this@asJavaStringLiteral) {
        append(when (char) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> char.toString()
        })
    }
} + "\""

android {
    namespace = "com.mytvsetup.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mytvsetup.launcher"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        for (key in listOf("TMDB_KEY", "YT_CLIENT_ID", "YT_CLIENT_SECRET")) {
            buildConfigField("String", key, secrets.getProperty(key, "").asJavaStringLiteral())
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Keep Robolectric's download lock and Maven cache inside the build environment.
            it.systemProperty("user.home", gradle.gradleUserHomeDir.absolutePath)
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )
        }
    }
    buildTypes {
        debug { buildConfigField("boolean", "OFFLINE", providers.gradleProperty("tv.offline").orElse("false").get()) }
        release {
            buildConfigField("boolean", "OFFLINE", "false")
            // Debug builds run un-optimised Compose, far too slow on the stick. Release is shrunk and
            // signed with the local debug key so it installs over the debug build and keeps its data.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
roborazzi {
    outputDir.set(rootProject.layout.projectDirectory.dir("shots"))
}
