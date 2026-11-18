plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.generator.impl"
}

dependencies {
    api(project(":feature:generator:interface"))
    implementation(project(":core:designsystem"))
}
