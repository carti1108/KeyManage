import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// iOS 앱이 `import KeyManageShared`로 쓰는 단일 Kotlin 프레임워크 (domain-migration-plan 12.2).
// 앱에는 Kotlin 프레임워크를 하나만 넣을 수 있으므로 공유 RIB·도메인·ribs-core를 이 프레임워크에 export한다
val appleFrameworkName = "KeyManageShared"

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    val xcFramework = XCFramework(appleFrameworkName)
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
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
        }
    }
}
