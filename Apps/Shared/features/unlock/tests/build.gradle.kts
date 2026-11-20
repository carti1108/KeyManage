// Unlock 피처의 테스트. Tuist의 Tests 타깃처럼 구현과 따로 둔다 (modularization-plan 3장 4번)
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":features:unlock:implementation"))
            implementation(project(":features:unlock:testing"))
            implementation(project(":core:testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
