plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.generator.tests"
}

dependencies {
    testImplementation(project(":feature:generator:implementation"))
    testImplementation(project(":feature:generator:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
