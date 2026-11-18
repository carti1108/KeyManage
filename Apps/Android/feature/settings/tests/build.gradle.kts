plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.settings.tests"
}

dependencies {
    testImplementation(project(":feature:settings:implementation"))
    testImplementation(project(":feature:settings:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
