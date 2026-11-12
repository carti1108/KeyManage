// 기기 보안 저장소(키체인 등). 앱이 주입하지 않고 플랫폼별 소스에서 Kotlin으로 구현한다 (target-architecture.md)
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
