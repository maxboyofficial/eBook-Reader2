// Build script for the Rust‑only Mochi eBook Reader app
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mochi.rust"
    compileSdk = 36

    defaultConfig {
        versionCode = 2
        versionName = "1.1"
        applicationId = "com.mochi.rust"
        minSdk = 24
        targetSdk = 36
        // Native activity uses a shared library named "mochi_rust_app"
        externalNativeBuild {}
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

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
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
