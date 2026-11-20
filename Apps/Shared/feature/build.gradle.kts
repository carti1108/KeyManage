// 웹·Android·iOS가 함께 쓰는 화면 로직(RIB). 뷰는 플랫폼이 XxxPresentable로 구현한다 (domain-migration-plan 11장)
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    // 테스트는 JVM에서 돈다. 화면을 쓰는 곳은 웹(js), Android, Apple이다 (android-migration-plan 4장 1단계)
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
            api(project(":domain"))
            api("com.carti.ribs:ribs-core")
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
