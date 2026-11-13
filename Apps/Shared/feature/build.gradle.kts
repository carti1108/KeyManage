// 웹·iOS가 함께 쓰는 화면 로직(RIB). 뷰는 플랫폼이 XxxPresentable로 구현한다 (domain-migration-plan 11장)
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    // 테스트는 JVM에서 돈다. 화면을 쓰는 곳은 웹(js)과 Apple이다. Android 뷰는 아직 없어 대상에서 뺐다
    jvm()
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
