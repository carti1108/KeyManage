plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.unlock.impl"
}

dependencies {
    api(project(":feature:unlock:interface"))
    implementation(project(":core:designsystem"))
}
