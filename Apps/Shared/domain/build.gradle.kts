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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
