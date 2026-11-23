plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mochi.rust"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mochi.rust"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Native activity uses a shared library named "mochi_rust_app"
        externalNativeBuild {
            // No CMake; we will copy the .so via a custom task.
        }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    // Keep assets minimal – just a placeholder string.
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java") // will be empty
            res.srcDirs("src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}

// Task that builds the Rust shared library and copies it into jniLibs.
val cargoBuild = tasks.register("cargoBuild") {
    doFirst {
        exec { commandLine("cargo", "--version") }
    }
    doLast {
        exec {
            workingDir("../rust-app")
            commandLine("cargo", "build", "--release", "--target", "aarch64-linux-android")
        }
        val libPath = file("../rust-app/target/aarch64-linux-android/release/libmochi_rust_app.so")
        val jniDir = file("src/main/jniLibs/arm64-v8a")
        jniDir.mkdirs()
        copy { from(libPath); into(jniDir); rename { "libmochi_rust_app.so" } }
    }
}

tasks.named("preBuild").configure { dependsOn(cargoBuild) }
