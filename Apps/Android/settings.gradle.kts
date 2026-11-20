pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KeyManageAndroid"

include(":app")

// Clean Architecture 계층 (ADR-AND-001)
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:rib")
// 피처 Example 앱의 공통 틀 (modularization-plan 3장 5번)
include(":core:example")

// 마이크로 피처: interface · implementation · testing · tests (uber/RIBs, 앱 전환 때 지운다)
// ui · example: 공유 RIB에 붙는 Compose 뷰와 그 피처만 띄우는 Example 앱 (modularization-plan 2장)
include(":feature:auth:interface")
include(":feature:auth:implementation")
include(":feature:auth:testing")
include(":feature:auth:tests")
include(":feature:auth:ui")
include(":feature:auth:example")
include(":feature:unlock:interface")
include(":feature:unlock:implementation")
include(":feature:unlock:testing")
include(":feature:unlock:tests")
include(":feature:unlock:ui")
include(":feature:unlock:example")
include(":feature:vaultlist:interface")
include(":feature:vaultlist:implementation")
include(":feature:vaultlist:testing")
include(":feature:vaultlist:tests")
include(":feature:vaultlist:ui")
include(":feature:vaultlist:example")
include(":feature:generator:interface")
include(":feature:generator:implementation")
include(":feature:generator:testing")
include(":feature:generator:tests")
include(":feature:generator:ui")
include(":feature:generator:example")
include(":feature:settings:interface")
include(":feature:settings:implementation")
include(":feature:settings:testing")
include(":feature:settings:tests")
include(":feature:main:interface")
include(":feature:main:implementation")
include(":feature:main:testing")
include(":feature:main:tests")
include(":feature:root:interface")
include(":feature:root:implementation")
include(":feature:root:testing")
include(":feature:root:tests")

// 공유 Kotlin 모듈(Apps/Shared). 화면 로직·도메인·플랫폼 구현을 여기서 가져온다 (android-migration-plan 4장 1단계)
includeBuild("../Shared") {
    dependencySubstitution {
        substitute(module("com.keymanage.shared:android-session")).using(project(":android-session"))
        substitute(module("com.keymanage.shared:vaultlist-interface")).using(project(":features:vaultlist:interface"))
        substitute(module("com.keymanage.shared:vaultlist-implementation")).using(project(":features:vaultlist:implementation"))
        substitute(module("com.keymanage.shared:vaultlist-testing")).using(project(":features:vaultlist:testing"))
        substitute(module("com.keymanage.shared:generator-interface")).using(project(":features:generator:interface"))
        substitute(module("com.keymanage.shared:generator-implementation")).using(project(":features:generator:implementation"))
        substitute(module("com.keymanage.shared:generator-testing")).using(project(":features:generator:testing"))
        substitute(module("com.keymanage.shared:auth-interface")).using(project(":features:auth:interface"))
        substitute(module("com.keymanage.shared:auth-implementation")).using(project(":features:auth:implementation"))
        substitute(module("com.keymanage.shared:auth-testing")).using(project(":features:auth:testing"))
        substitute(module("com.keymanage.shared:unlock-interface")).using(project(":features:unlock:interface"))
        substitute(module("com.keymanage.shared:unlock-implementation")).using(project(":features:unlock:implementation"))
        substitute(module("com.keymanage.shared:unlock-testing")).using(project(":features:unlock:testing"))
    }
}
