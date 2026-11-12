// KeyManage 공유 Kotlin 코드 (실험 브랜치 research/kmp-migration)
rootProject.name = "keymanage-shared"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":crypto")
include(":secure-store")
