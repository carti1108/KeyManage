plugins {
    alias(libs.plugins.km.android.application)
}

android {
    namespace = "com.keymanage.android"
    defaultConfig {
        applicationId = "com.keymanage.android"
        versionCode = 1
        versionName = "0.1.0"
    }
}

// 조립은 :app에서만 한다. 피처는 implementation, 나머지는 interface로 본다(ADR-AND-001)
dependencies {
    implementation(project(":core:data"))
    implementation(project(":feature:root:implementation"))
    implementation(libs.androidx.core.ktx)
}
