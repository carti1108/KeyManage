// 공유 domain의 서버 포트(AuthApi·VaultApi)를 Ktor Client로 구현한다 (ADR-0026)
// 원본: Apps/Web/src/adapters/http/. 엔진은 대상마다 다르고(JS fetch, OkHttp, Darwin, JVM은 CIO), 공통 코드는 엔진을 모른다
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "com.keymanage.serverhttp"
        compileSdk = 37
        minSdk = 26
    }
    js {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
        // 5MB 볼트를 여러 번 암호화하는 테스트가 Mocha 기본 한도(2초)를 넘을 수 있다. 브라우저(karma.config.d)와 같게 둔다
        nodejs { testTask { useMocha { timeout = "120s" } } }
    }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jsMain.dependencies { implementation(libs.ktor.client.js) }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        appleMain.dependencies { implementation(libs.ktor.client.darwin) }
        jvmMain.dependencies { implementation(libs.ktor.client.cio) }
    }
}
