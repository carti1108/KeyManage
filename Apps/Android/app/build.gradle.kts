plugins {
    alias(libs.plugins.km.android.application)
}

android {
    namespace = "com.keymanage.android"
    defaultConfig {
        applicationId = "com.keymanage.android"
        versionCode = 1
        versionName = "0.1.0"
        // 서버 주소. 기본은 adb reverse로 잇는 로컬 백엔드다. 다른 서버는 -Pkeymanage.serverUrl=https://... 로 넘긴다
        val serverUrl = providers.gradleProperty("keymanage.serverUrl").getOrElse("http://localhost:8080")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

// 조립은 :app에서만 한다. 피처는 implementation, 나머지는 interface로 본다(ADR-AND-001)
dependencies {
    implementation(project(":core:data"))
    implementation(project(":feature:root:implementation"))
    implementation(project(":feature:auth:implementation"))
    implementation(libs.androidx.core.ktx)
}
