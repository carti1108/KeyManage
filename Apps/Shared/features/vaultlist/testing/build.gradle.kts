// VaultList 피처의 테스트 더블과 시나리오. 테스트와 각 플랫폼의 Example 앱이 함께 쓴다
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":features:vaultlist:interface"))
            // 폼 안의 생성기(자식 RIB)까지 띄우는 시나리오가 생성기의 더블을 쓴다
            api(project(":features:generator:testing"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
