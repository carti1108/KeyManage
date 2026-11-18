plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.auth"
}

dependencies {
    api(project(":core:rib"))
    api(project(":core:domain"))
}
