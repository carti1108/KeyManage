plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.auth.impl"
}

dependencies {
    api(project(":feature:auth:interface"))
    implementation(project(":core:designsystem"))
}
