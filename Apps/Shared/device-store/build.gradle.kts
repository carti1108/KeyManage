// 기기 볼트 기록 저장소(VLT-002·VLT-003). 기록 형식은 공통, 저장 위치만 플랫폼별이다 (target-architecture.md)
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    // 파일 구현(FileDeviceRecordStore)은 java.io만 쓴다. Android가 쓰고, 테스트는 JVM에서 돈다 (android-migration-plan 4장 1단계)
    jvm()
    android {
        namespace = "com.keymanage.devicestore"
        compileSdk = 37
        minSdk = 26
    }
    // 웹은 IndexedDB 구현을 쓴다. Apple 구현은 이후 같은 포트로 추가한다
    js {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // jvm·android 중간 소스셋은 JDK API를 볼 수 없어, 같은 폴더를 두 소스셋에 함께 넣는다
        val fileSources = "src/fileMain/kotlin"
        jvmMain {
            kotlin.srcDir(fileSources)
            dependencies { api(project(":domain")) }
        }
        androidMain {
            kotlin.srcDir(fileSources)
            dependencies { api(project(":domain")) }
        }
    }
}
