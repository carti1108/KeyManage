plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.main.tests"
}

dependencies {
    testImplementation(project(":feature:main:implementation"))
    testImplementation(project(":feature:main:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
