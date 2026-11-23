// 웹·Android·iOS가 함께 쓰는 화면 로직(RIB). 뷰는 플랫폼이 XxxPresentable로 구현한다
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)
    // 테스트는 JVM에서 돈다. 화면을 쓰는 곳은 웹(js), Android, Apple이다
    jvm()
    android {
        namespace = "com.keymanage.feature"
        compileSdk = 37
        minSdk = 26
    }
    js { browser() }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:feature-base"))
            // 나눈 피처. 부모 RIB(Root)은 interface만 보고, 구현 Builder는 여기서 넣는다
            api(project(":features:unlock:implementation"))
            api(project(":features:auth:implementation"))
            api(project(":features:generator:implementation"))
            api(project(":features:vaultlist:implementation"))
            api(project(":features:settings:implementation"))
            api(project(":features:main:implementation"))
            api("com.carti.ribs:ribs-core")
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":core:testing"))
            implementation(project(":features:unlock:testing"))
            implementation(project(":features:auth:testing"))
            implementation(project(":features:generator:testing"))
            implementation(project(":features:vaultlist:testing"))
            implementation(project(":features:settings:testing"))
            implementation(project(":features:main:testing"))
        }
    }
}
