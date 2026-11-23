// iOS 피처 Example 앱과 Swift 뷰 테스트가 쓰는 테스트용 묶음 프레임워크. 운영용(apple-session)과 같은 이름 KeyManageShared로 내고,
// 피처 구현 Builder와 testing 모듈(가짜 세션, 시나리오)을 더 export한다. 앱 번들에는 들어가지 않는다
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

evaluationDependsOn(":apple-session")

@Suppress("UNCHECKED_CAST")
val appleExports = project(":apple-session").extra["appleExports"] as List<String>
val features = listOf("unlock", "auth", "generator", "vaultlist", "settings", "main")
val exampleExports = appleExports +
    ":core:testing" +
    features.flatMap { listOf(":features:$it:implementation", ":features:$it:testing") }

kotlin {
    val xcFramework = XCFramework("KeyManageShared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KeyManageShared"
            isStatic = true
            exampleExports.forEach { export(project(it)) }
            export("com.carti.ribs:ribs-core")
            xcFramework.add(this)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        iosMain.dependencies {
            exampleExports.forEach { api(project(it)) }
            api("com.carti.ribs:ribs-core")
        }
    }
}
