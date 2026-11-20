// Auth 피처의 테스트 더블과 시나리오. 테스트와 각 플랫폼의 Example 앱이 함께 쓴다
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":features:auth:interface"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
