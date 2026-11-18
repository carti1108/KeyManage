plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.vaultlist.tests"
}

dependencies {
    testImplementation(project(":feature:vaultlist:implementation"))
    testImplementation(project(":feature:vaultlist:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
