plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.root"
}

dependencies {
    api(project(":core:rib"))
}
