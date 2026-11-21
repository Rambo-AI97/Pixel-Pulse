import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing + Nothing's production key live outside the repo (never committed):
// ~/.config/pixel-pulse/keystore.properties (storeFile, storePassword, keyAlias, keyPassword)
// ~/.config/pixel-pulse/nothing.key (one line; until it exists, builds use Nothing's "test" key)
val cfgDir = File(System.getProperty("user.home"), ".config/pixel-pulse")
val keyProps = Properties().apply {
    val f = File(cfgDir, "keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val nothingKeyFile = File(cfgDir, "nothing.key")
val nothingKey = if (nothingKeyFile.exists()) nothingKeyFile.readText().trim() else "test"

android {
    namespace = "com.ramboai97.pixelpulse"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ramboai97.pixelpulse"
        minSdk = 34
        targetSdk = 36
        versionCode = 9
        versionName = "1.1"
        manifestPlaceholders["nothingKey"] = nothingKey
    }
    signingConfigs {
        if (keyProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = File(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
}
