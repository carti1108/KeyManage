plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.main.testing"
}

dependencies {
    api(project(":feature:main:interface"))
}
