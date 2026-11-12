// 볼트 암호 규격(docs/vault-spec.md 2장) 구현. 실제 연산은 cryptography-kotlin이 플랫폼 구현에 맡긴다
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm()
    js {
        browser()
        nodejs()
    }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest {
            kotlin.srcDir(layout.buildDirectory.dir("generated/testVectors"))
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

// docs/crypto-test-vectors.json을 공통 테스트가 읽을 수 있게 Kotlin 상수로 만든다. 파일 읽기 API가 대상마다 달라서다
val vectorsFile = rootProject.layout.projectDirectory.file("../../docs/crypto-test-vectors.json")
val generateTestVectors by tasks.registering {
    inputs.file(vectorsFile)
    val outDir = layout.buildDirectory.dir("generated/testVectors")
    outputs.dir(outDir)
    doLast {
        val json = vectorsFile.asFile.readText()
        val escaped = json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$").replace("\n", "\\n")
        val target = outDir.get().file("com/keymanage/crypto/TestVectorsJson.kt").asFile
        target.parentFile.mkdirs()
        target.writeText("package com.keymanage.crypto\n\ninternal const val TEST_VECTORS_JSON = \"$escaped\"\n")
    }
}
tasks.matching { it.name.startsWith("compileTestKotlin") }.configureEach { dependsOn(generateTestVectors) }
