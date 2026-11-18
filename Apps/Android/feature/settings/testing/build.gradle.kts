plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.settings.testing"
}

dependencies {
    api(project(":feature:settings:interface"))
}
