plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.settings.impl"
}

dependencies {
    api(project(":feature:settings:interface"))
    implementation(project(":core:designsystem"))
}
