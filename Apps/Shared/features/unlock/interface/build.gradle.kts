// Unlock 피처의 계약: Dependency·Listener·Buildable·Routing·화면 계약·화면 상태. 다른 피처와 플랫폼 뷰는 이 모듈만 본다
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:feature-base"))
            api("com.carti.ribs:ribs-core")
        }
    }
}
