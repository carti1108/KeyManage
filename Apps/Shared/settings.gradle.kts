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
include(":device-store")
include(":domain")
include(":server-http")
include(":web-session")
include(":feature")

// 공유 RIB 라이브러리. 같은 상위 폴더에 carti-RIBs-KMP 저장소가 있어야 한다 (domain-migration-plan 11.2)
includeBuild("../../../carti-RIBs-KMP") {
    dependencySubstitution {
        substitute(module("com.carti.ribs:ribs-core")).using(project(":ribs-core"))
        substitute(module("com.carti.ribs:ribs-web")).using(project(":ribs-web"))
    }
}
