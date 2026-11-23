// iOS 앱(Apps/Apple)이 가져다 쓰는 모듈. 공유 RIB·세션에 넣을 iOS 쪽 포트 구현과 앱 진입점을 둔다 (android-session의 짝)
// 운영용 묶음 프레임워크 KeyManageShared를 만든다. 테스트용 묶음은 apple-example이 같은 이름으로 만든다
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

/** Swift 뷰가 보는 공유 모듈. 운영용·테스트용 묶음이 함께 export한다 */
val appleExports = listOf(
    ":app",
    ":core:domain",
    ":core:feature-base",
    ":features:unlock:interface",
    ":features:auth:interface",
    ":features:generator:interface",
    ":features:vaultlist:interface",
    ":features:settings:interface",
    ":features:main:interface",
)
extra["appleExports"] = appleExports

kotlin {
    val xcFramework = XCFramework("KeyManageShared")
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KeyManageShared"
            isStatic = true
            appleExports.forEach { export(project(it)) }
            export("com.carti.ribs:ribs-core")
            xcFramework.add(this)
        }
    }

    applyDefaultHierarchyTemplate()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        appleMain.dependencies {
            appleExports.forEach { api(project(it)) }
            api("com.carti.ribs:ribs-core")
            api(project(":core:server-http"))
            api(project(":core:secure-store"))
            implementation(libs.kotlinx.coroutines.core)
        }
        appleTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
