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
include(":ios-app")

// 공유 RIB 라이브러리(carti-RIBs-KMP). 기본은 이 저장소와 같은 상위 폴더의 형제 폴더다 (domain-migration-plan 11.2).
// 저장소 밖에 둘 수 없는 환경(CI는 워크스페이스 안에만 checkout할 수 있다)은 환경 변수 CARTI_RIBS_DIR로 경로를 바꾼다
val cartiRibsDir = providers.environmentVariable("CARTI_RIBS_DIR").orElse("../../../carti-RIBs-KMP").get()
includeBuild(cartiRibsDir) {
    dependencySubstitution {
        substitute(module("com.carti.ribs:ribs-core")).using(project(":ribs-core"))
        substitute(module("com.carti.ribs:ribs-web")).using(project(":ribs-web"))
    }
}
