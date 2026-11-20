// Android 앱(Apps/Android)이 가져다 쓰는 모듈. 공유 RIB·세션에 넣을 Android 쪽 포트 구현과 앱 진입점을 둔다 (web-session의 Android 짝)
// 설계: docs/research/kmp-migration/android-migration-plan.md 4장
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "com.keymanage.androidsession"
        compileSdk = 37
        minSdk = 26
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        androidMain.dependencies {
            api(project(":feature"))
            api(project(":server-http"))
            api(project(":device-store"))
            api(project(":secure-store"))
            implementation(libs.kotlinx.coroutines.android)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.junit)
        }
    }
}
