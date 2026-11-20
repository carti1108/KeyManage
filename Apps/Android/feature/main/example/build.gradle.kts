// Main 피처만 띄우는 Example 앱. 공유 testing 모듈의 시나리오로 RIB을 만들어 실제 뷰에 붙이고, 기기에서 눈으로 확인한다 (modularization-plan 3장 5번)
plugins {
    alias(libs.plugins.km.android.application)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.main.example"
    defaultConfig {
        applicationId = "com.keymanage.android.feature.main.example"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":feature:main:ui"))
    implementation(project(":core:example"))
    implementation("com.keymanage.shared:main-implementation")
    implementation("com.keymanage.shared:main-testing")
    // 탭 안의 자식 RIB(볼트 목록·생성기·설정)의 뷰와 구현
    implementation(project(":feature:vaultlist:ui"))
    implementation("com.keymanage.shared:vaultlist-implementation")
    implementation(project(":feature:generator:ui"))
    implementation("com.keymanage.shared:generator-implementation")
    implementation(project(":feature:settings:ui"))
    implementation("com.keymanage.shared:settings-implementation")
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
