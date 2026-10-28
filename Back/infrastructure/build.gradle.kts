// 어댑터: 볼트 암호문 저장소 등 도메인 포트를 구현한다 (ADR-0002, ADR-0003)
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":domain"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // 인증 해시 재해시용 Argon2id (SEC-002). spring-security-crypto는 BOM이 관리하고, 실제 구현인 Bouncy Castle은 BOM에 없어 버전을 적는다
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
