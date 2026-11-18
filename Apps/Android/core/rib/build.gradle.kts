plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.rib"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.ribs.android)
    api(libs.ribs.android.compose)
    api(libs.ribs.coroutines)
    testImplementation(libs.kotlinx.coroutines.test)
}
