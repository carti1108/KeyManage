plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.root.testing"
}

dependencies {
    api(project(":feature:root:interface"))
}
