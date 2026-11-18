plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.auth.testing"
}

dependencies {
    api(project(":feature:auth:interface"))
}
