import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// 플러그인 버전은 여기서만 선언하고, 실제 적용은 각 모듈이 필요한 것만 한다
plugins {
    // Kotlin 버전은 Spring Boot 3.4 BOM이 관리하는 버전(1.9.25)과 맞춘다
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    id("org.springframework.boot") version "3.4.13" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// 모든 모듈 공통 설정
subprojects {
    group = "com.keymanage"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    // 라이브러리 버전은 Spring Boot BOM으로 통일한다 (버전 목록만 가져오며, 라이브러리 자체를 추가하지는 않는다)
    apply(plugin = "io.spring.dependency-management")
    configure<DependencyManagementExtension> {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
        configure<KotlinJvmProjectExtension> {
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict")
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
