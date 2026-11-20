// 공유 Kotlin 모듈의 공통 설정: 대상(JVM·Android·JS·iOS·macOS), 툴체인, 컴파일 옵션.
// 모듈의 build.gradle.kts에는 의존성만 적는다 (modularization-plan 3장 7번)
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)
    // 테스트는 JVM에서 돈다
    jvm()
    android {
        // ":features:unlock:interface" → com.keymanage.shared.features_unlock_interface (interface는 패키지 이름으로 쓸 수 없다)
        namespace = "com.keymanage.shared." + project.path.trim(':').replace(':', '_').replace('-', '_')
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
}
