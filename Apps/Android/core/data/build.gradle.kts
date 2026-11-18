plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.data"
}

dependencies {
    api(project(":core:domain"))
}
