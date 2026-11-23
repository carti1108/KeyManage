// 웹 피처 Example이 가져다 쓰는 Kotlin/JS 모듈. 피처의 Testing 시나리오로 공유 RIB 하나를 웹 뷰에 붙인다
// 가짜 세션과 시나리오가 들어 있어 배포 번들(web-session)과 따로 빌드한다. web-session의 내보내기를 그대로 포함한다
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    js {
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
        useEsModules()
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        jsMain.dependencies {
            // 웹 뷰 구현(WebXxxView)과 공유 RIB
            api(project(":web-session"))
            implementation(project(":core:testing"))
            implementation(project(":features:unlock:testing"))
            implementation(project(":features:auth:testing"))
            implementation(project(":features:generator:testing"))
            implementation(project(":features:vaultlist:testing"))
            implementation(project(":features:settings:testing"))
            implementation(project(":features:main:testing"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
