plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.vaultlist.impl"
}

dependencies {
    api(project(":feature:vaultlist:interface"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
}
