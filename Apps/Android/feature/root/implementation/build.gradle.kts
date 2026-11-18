plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.root.impl"
}

dependencies {
    api(project(":feature:root:interface"))
    implementation(project(":core:designsystem"))
}
