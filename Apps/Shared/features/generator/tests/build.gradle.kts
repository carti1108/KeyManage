// PasswordGenerator 피처의 테스트. Tuist의 Tests 타깃처럼 구현과 따로 둔다
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":features:generator:implementation"))
            implementation(project(":features:generator:testing"))
            implementation(project(":core:testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
