// VaultList 피처만 띄우는 Example 앱. 공유 testing 모듈의 시나리오로 RIB을 만들어 실제 뷰에 붙이고, 기기에서 눈으로 확인한다 (modularization-plan 3장 5번)
plugins {
    alias(libs.plugins.km.android.application)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.vaultlist.example"
    defaultConfig {
        applicationId = "com.keymanage.android.feature.vaultlist.example"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":feature:vaultlist:ui"))
    implementation(project(":core:example"))
    implementation("com.keymanage.shared:vaultlist-implementation")
    implementation("com.keymanage.shared:vaultlist-testing")
    // 폼 안의 비밀번호 생성기(자식 RIB)의 뷰와 구현
    implementation(project(":feature:generator:ui"))
    implementation("com.keymanage.shared:generator-implementation")
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
