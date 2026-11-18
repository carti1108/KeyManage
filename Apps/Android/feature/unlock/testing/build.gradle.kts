plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.unlock.testing"
}

dependencies {
    api(project(":feature:unlock:interface"))
}
