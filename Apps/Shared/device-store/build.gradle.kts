// 기기 볼트 기록 저장소(VLT-002·VLT-003). 기록 형식은 공통, 저장 위치만 플랫폼별이다 (target-architecture.md)
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // 웹은 IndexedDB(jsMain), Apple은 앱 Application Support 폴더의 파일(appleMain). Android는 이후 같은 인터페이스로 추가한다
    js {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
