// VaultList 피처의 RIB 구현(Builder·Interactor·Router)
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":features:vaultlist:interface"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
