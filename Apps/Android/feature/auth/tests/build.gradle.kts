plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.auth.tests"
}

dependencies {
    testImplementation(project(":feature:auth:implementation"))
    testImplementation(project(":feature:auth:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
