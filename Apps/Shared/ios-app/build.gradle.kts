import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// iOS·macOS 앱이 `import KeyManageShared`로 쓰는 단일 Kotlin 프레임워크 (domain-migration-plan 12.2, 12.5 macOS 전환).
// 두 플랫폼이 같은 코드는 appleMain, UIKit만 iosMain, AppKit만 macosMain에 둔다. 모듈 이름은 옛 Swift 경로를 지우는 단계까지 그대로 둔다
// 앱에는 Kotlin 프레임워크를 하나만 넣을 수 있으므로 공유 RIB·도메인·ribs-core를 이 프레임워크에 export한다
val appleFrameworkName = "KeyManageShared"

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    val xcFramework = XCFramework(appleFrameworkName)
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = appleFrameworkName
            isStatic = true
            export(project(":feature"))
            export(project(":domain"))
            export("com.carti.ribs:ribs-core")
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":feature"))
            api(project(":domain"))
            api("com.carti.ribs:ribs-core")
            // Apple 플랫폼 구현(domain-migration-plan 12.3의 1단계). 진입점만 쓰므로 export하지 않는다
            implementation(project(":device-store"))
            implementation(project(":secure-store"))
            implementation(project(":server-http"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
