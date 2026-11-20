// KeyManage 공유 Kotlin 코드 (실험 브랜치 research/kmp-migration)
rootProject.name = "keymanage-shared"

pluginManagement {
    includeBuild("build-logic")
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

// 피처가 함께 쓰는 것 (modularization-plan 2장)
include(":core:feature-base")

// 마이크로 피처: interface · implementation · testing · tests
include(":features:unlock:interface")
include(":features:unlock:implementation")
include(":features:unlock:testing")
include(":features:unlock:tests")
include(":features:auth:interface")
include(":features:auth:implementation")
include(":features:auth:testing")
include(":features:auth:tests")
include(":features:generator:interface")
include(":features:generator:implementation")
include(":features:generator:testing")
include(":features:generator:tests")
include(":features:vaultlist:interface")
include(":features:vaultlist:implementation")
include(":features:vaultlist:testing")
include(":features:vaultlist:tests")
include(":features:settings:interface")
include(":features:settings:implementation")
include(":features:settings:testing")
include(":features:settings:tests")
include(":features:main:interface")
include(":features:main:implementation")
include(":features:main:testing")
include(":features:main:tests")

// 공용 테스트 더블 (modularization-plan 2장)
include(":core:testing")
include(":android-session")

// 공유 RIB 라이브러리(carti-RIBs-KMP). 기본은 이 저장소와 같은 상위 폴더의 형제 폴더다 (domain-migration-plan 11.2).
// 저장소 밖에 둘 수 없는 환경(CI는 워크스페이스 안에만 checkout할 수 있다)은 환경 변수 CARTI_RIBS_DIR로 경로를 바꾼다
val cartiRibsDir = providers.environmentVariable("CARTI_RIBS_DIR").orElse("../../../carti-RIBs-KMP").get()
includeBuild(cartiRibsDir) {
    dependencySubstitution {
        substitute(module("com.carti.ribs:ribs-core")).using(project(":ribs-core"))
        substitute(module("com.carti.ribs:ribs-web")).using(project(":ribs-web"))
        // Android 앱(Apps/Android)의 Compose 뷰가 쓴다. 이 빌드를 composite로 가져간 쪽에도 같은 치환이 적용된다
        substitute(module("com.carti.ribs:ribs-compose")).using(project(":ribs-compose"))
    }
}
