plugins {
    alias(libs.plugins.km.jvm.library)
}

dependencies {
    // 생체 키 포트가 suspend라 테스트에서만 runTest를 쓴다
    testImplementation(libs.kotlinx.coroutines.test)
}
