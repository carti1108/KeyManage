// Settings 피처의 Compose 뷰. 공유 RIB의 화면 계약(interface)과 디자인 시스템에만 의존한다 (modularization-plan 2장)
plugins {
    alias(libs.plugins.km.android.library)
    alias(libs.plugins.km.android.compose)
}

android {
    namespace = "com.keymanage.android.feature.settings.ui"
}

dependencies {
    api("com.keymanage.shared:settings-interface")
    api("com.carti.ribs:ribs-compose")
    implementation(project(":core:designsystem"))
}
