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

// 조립은 :app에서만 한다. 피처는 implementation, 나머지는 interface로 본다(ADR-AND-001)
dependencies {
    implementation(project(":core:data"))
    implementation(project(":feature:root:implementation"))
    implementation(project(":feature:auth:implementation"))
    implementation(project(":feature:unlock:implementation"))
    implementation(project(":feature:vaultlist:implementation"))
    implementation(project(":feature:generator:implementation"))
    implementation(project(":feature:settings:implementation"))
    implementation(project(":feature:main:implementation"))
    // 공유 RIB·세션과 Android 쪽 포트 구현. 화면 연결 전이라 아직 쓰는 코드는 없다 (android-migration-plan 4장)
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
}
