plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Chaque construction sur GitHub augmente le numéro de version,
// pour que la nouvelle version s'installe par-dessus l'ancienne (sans perdre les trophées).
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.thomas.sudoku"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thomas.sudoku"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("sudoku") {
            storeFile = file("sudoku.jks")
            storePassword = "sudoku"
            keyAlias = "sudoku"
            keyPassword = "sudoku"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sudoku")
        }
        debug {
            signingConfig = signingConfigs.getByName("sudoku")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
