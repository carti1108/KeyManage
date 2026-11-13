// 웹 앱(Apps/Web)이 가져다 쓰는 Kotlin/JS 내보내기 모듈. domain의 VaultSession과 server-http를 묶어 @JsExport 퍼사드로 낸다
// 설계: docs/research/kmp-migration/domain-migration-plan.md 10·11장. 브라우저 포트(기기 저장소·탭 잠금·탭 채널·탭 기억)는 TypeScript 객체를 받는다
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
        browser {
            // 5MB 볼트를 여러 번 암호화하는 테스트가 Mocha 기본 한도(2초)를 넘을 수 있다. 한도는 karma.config.d에서 늘린다
            testTask { useKarma { useChromeHeadless() } }
        }
        binaries.library()
        generateTypeScriptDefinitions()
        useEsModules()
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        jsMain.dependencies {
            api(project(":domain"))
            api(project(":server-http"))
            // 공유 RIB과 웹 어댑터(ViewStore·ScreenSlot). 웹 뷰 구현(WebXxxView)과 진입점 WebKeyManageApp이 쓴다 (domain-migration-plan 11장)
            api(project(":feature"))
            api("com.carti.ribs:ribs-web")
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
