plugins {
    alias(libs.plugins.km.android.library)
}

android {
    namespace = "com.keymanage.android.data"
}

dependencies {
    api(project(":core:domain"))
    // Android 단위 테스트의 org.json은 스텁이라, 벡터 JSON을 읽을 실제 구현을 넣는다
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
}

// 플랫폼 공용 암호 테스트 벡터(vault-spec 2장). 생성기로 만든 파일을 그대로 읽는다
tasks.withType<Test>().configureEach {
    systemProperty("km.cryptoVectors", rootProject.file("../../docs/crypto-test-vectors.json").absolutePath)
}
