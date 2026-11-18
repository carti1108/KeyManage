plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.root.tests"
}

dependencies {
    testImplementation(project(":feature:root:implementation"))
    testImplementation(project(":feature:root:testing"))
}
