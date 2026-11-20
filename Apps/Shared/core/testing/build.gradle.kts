// 여러 피처의 테스트와 Example 앱이 함께 쓰는 테스트 더블(가짜 세션·환경·클립보드 등). MusicSearch의 MSTesting에 해당한다
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:feature-base"))
            api("com.carti.ribs:ribs-core")
            api(libs.kotlinx.coroutines.test)
        }
    }
}
