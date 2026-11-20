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

// 뷰가 함께 쓰는 것
include(":core:designsystem")
// 피처 Example 앱의 공통 틀 (modularization-plan 3장 5번)
include(":core:example")

// 피처마다 ui(공유 RIB에 붙는 Compose 뷰)와 example(그 피처만 띄우는 앱). RIB·도메인은 Apps/Shared에 있다 (modularization-plan 2장)
include(":feature:auth:ui")
include(":feature:auth:example")
include(":feature:unlock:ui")
include(":feature:unlock:example")
include(":feature:vaultlist:ui")
include(":feature:vaultlist:example")
include(":feature:generator:ui")
include(":feature:generator:example")
include(":feature:settings:ui")
include(":feature:settings:example")
include(":feature:main:ui")
include(":feature:main:example")

// 공유 Kotlin 모듈(Apps/Shared). 화면 로직·도메인·플랫폼 구현을 여기서 가져온다 (android-migration-plan 4장 1단계)
includeBuild("../Shared") {
    dependencySubstitution {
        substitute(module("com.keymanage.shared:android-session")).using(project(":android-session"))
        substitute(module("com.keymanage.shared:main-interface")).using(project(":features:main:interface"))
        substitute(module("com.keymanage.shared:main-implementation")).using(project(":features:main:implementation"))
        substitute(module("com.keymanage.shared:main-testing")).using(project(":features:main:testing"))
        substitute(module("com.keymanage.shared:settings-interface")).using(project(":features:settings:interface"))
        substitute(module("com.keymanage.shared:settings-implementation")).using(project(":features:settings:implementation"))
        substitute(module("com.keymanage.shared:settings-testing")).using(project(":features:settings:testing"))
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
