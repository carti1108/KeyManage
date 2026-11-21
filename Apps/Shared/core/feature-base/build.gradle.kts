// 피처가 함께 쓰는 포트(SessionPort 등)와 사용자 문구. 피처의 interface 모듈이 의존한다
plugins {
    id("km.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
