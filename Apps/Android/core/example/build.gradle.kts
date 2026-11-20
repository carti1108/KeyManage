// 피처 Example 앱의 공통 틀(시나리오 목록 → RIB 하나 띄우기). MusicSearch의 Example 템플릿(DemoListViewController)에 해당한다
plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.example"
}

dependencies {
    api("com.carti.ribs:ribs-compose")
    api(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
}
