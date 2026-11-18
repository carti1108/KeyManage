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

// 마이크로 피처: interface · implementation · testing · tests
include(":feature:auth:interface")
include(":feature:auth:implementation")
include(":feature:auth:testing")
include(":feature:auth:tests")
include(":feature:root:interface")
include(":feature:root:implementation")
include(":feature:root:testing")
include(":feature:root:tests")
