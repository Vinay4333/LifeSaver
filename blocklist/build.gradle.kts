plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "app.lifesaver.blocklist"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    testImplementation(libs.junit)
}
