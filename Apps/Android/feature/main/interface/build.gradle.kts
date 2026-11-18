plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.main"
}

dependencies {
    api(project(":core:rib"))
    api(project(":core:domain"))
    api(project(":feature:vaultlist:interface"))
    api(project(":feature:generator:interface"))
    api(project(":feature:settings:interface"))
}
