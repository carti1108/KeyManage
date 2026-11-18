plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.feature.generator.testing"
}

dependencies {
    api(project(":feature:generator:interface"))
}
