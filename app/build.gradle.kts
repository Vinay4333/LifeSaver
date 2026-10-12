plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "app.lifesaver"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lifesaver"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Relay and approve page. Override with -PntfyBase=... only for emulator testing.
        val ntfyBase = providers.gradleProperty("ntfyBase").getOrElse("https://ntfy.sh")
        val approveBase =
            providers.gradleProperty("approveBase").getOrElse("https://vinay4333.github.io/LifeSaver/approve/")
        buildConfigField("String", "NTFY_BASE", "\"$ntfyBase\"")
        buildConfigField("String", "APPROVE_BASE", "\"$approveBase\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

ktlint {
    android.set(true)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt.yml"))
}

dependencies {
    implementation(project(":vpn"))
    implementation(project(":blocklist"))
    implementation(project(":policy"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
}
