// 공유 모듈의 빌드 규칙(convention plugin). MusicSearch의 Tuist Project+Templates에 해당한다
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "shared-build-logic"
include(":convention")
