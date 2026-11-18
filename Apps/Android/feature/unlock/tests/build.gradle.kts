plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.unlock.tests"
}

dependencies {
    testImplementation(project(":feature:unlock:implementation"))
    testImplementation(project(":feature:unlock:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
