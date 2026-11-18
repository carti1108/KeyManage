plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.main.impl"
}

dependencies {
    api(project(":feature:main:interface"))
    implementation(project(":core:designsystem"))
}
