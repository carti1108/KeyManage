// 기기 보안 저장소(키체인 등). 앱이 주입하지 않고 플랫폼별 소스에서 Kotlin으로 구현한다 (target-architecture.md)
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.keymanage.securestore"
        compileSdk = 37
        minSdk = 26
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    iosArm64()
    // 시뮬레이터 키체인은 앱 번들 + 바이너리의 __entitlements 섹션이 있어야 열린다 (Xcode 시뮬레이터 빌드와 같은 방식)
    iosSimulatorArm64 {
        binaries.getTest("debug").linkerOpts(
            "-sectcreate", "__TEXT", "__entitlements", file("hostTest/simulator.entitlements").absolutePath,
        )
    }
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.fragment.testing.manifest)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
