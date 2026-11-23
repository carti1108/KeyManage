plugins {
    alias(libs.plugins.km.android.application)
    alias(libs.plugins.km.android.compose)
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

// 공유 진입점(android-session)에 Compose 뷰를 넘겨 조립한다. 도메인·RIB·플랫폼 구현은 Apps/Shared에 있다
dependencies {
    implementation("com.keymanage.shared:android-session")
    implementation(project(":feature:auth:ui"))
    implementation(project(":feature:unlock:ui"))
    implementation(project(":feature:main:ui"))
    implementation(project(":feature:vaultlist:ui"))
    implementation(project(":feature:generator:ui"))
    implementation(project(":feature:settings:ui"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.kotlinx.coroutines.android)
}
