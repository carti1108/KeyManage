plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.generator"
}

dependencies {
    api(project(":core:rib"))
    api(project(":core:domain"))
}
