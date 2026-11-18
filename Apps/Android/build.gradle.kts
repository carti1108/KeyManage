// 플러그인 버전은 여기서 한 번만 정하고, 모듈은 build-logic의 km.* 플러그인으로 적용한다
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
