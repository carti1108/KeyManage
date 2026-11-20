plugins {
    `kotlin-dsl`
}

// 스크립트 플러그인이 kotlin { android { } } 접근자를 쓰려면 플러그인이 구현 의존성에 있어야 한다
dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.android.gradle.plugin)
}
