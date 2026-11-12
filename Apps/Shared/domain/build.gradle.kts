// 공유 도메인(볼트·인증·기기·세션). 웹 도메인(Apps/Web/src/domain)을 원본으로 옮긴다 (domain-migration-plan.md)
// commonMain에는 플랫폼 타입을 두지 않고, 시간·난수·저장소·서버는 모두 인터페이스로 받는다
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "com.keymanage.domain"
        compileSdk = 37
        minSdk = 26
    }
    js {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
        nodejs()
    }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.io.encoding.ExperimentalEncodingApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":crypto"))
            implementation(libs.cryptography.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest {
            kotlin.srcDir(layout.buildDirectory.dir("generated/testVectors"))
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// docs/crypto-test-vectors.json을 공통 테스트가 읽을 수 있게 Kotlin 상수로 만든다(crypto 모듈과 같은 방식)
val vectorsFile = rootProject.layout.projectDirectory.file("../../docs/crypto-test-vectors.json")
val generateTestVectors by tasks.registering {
    inputs.file(vectorsFile)
    val outDir = layout.buildDirectory.dir("generated/testVectors")
    outputs.dir(outDir)
    doLast {
        val json = vectorsFile.asFile.readText()
        val escaped = json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$").replace("\r", "").replace("\n", "\\n")
        val target = outDir.get().file("com/keymanage/domain/crypto/TestVectorsJson.kt").asFile
        target.parentFile.mkdirs()
        target.writeText("package com.keymanage.domain.crypto\n\ninternal const val TEST_VECTORS_JSON = \"$escaped\"\n")
    }
}
tasks.matching { it.name.startsWith("compileTestKotlin") }.configureEach { dependsOn(generateTestVectors) }
