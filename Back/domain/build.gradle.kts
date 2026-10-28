// 도메인: 모델, 포트, UseCase, 도메인 에러 (ADR-0002)
// Spring, Jackson 등 프레임워크와 외부 라이브러리를 의존하지 않는다 (순수 Kotlin + JUnit 5만 허용)
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 메인 클래스패스에 허용하는 그룹: Kotlin 표준 라이브러리와 그것이 끌어오는 org.jetbrains:annotations뿐이다
val allowedGroups = setOf("org.jetbrains.kotlin", "org.jetbrains")

// 누군가 domain에 프레임워크나 다른 모듈 의존을 추가하면 check가 실패하도록 물리적으로 막는다 (ADR-0002, ADR-0004)
val verifyFrameworkIsolation by tasks.registering {
    group = "verification"
    description = "domain 메인 클래스패스에 Kotlin 표준 라이브러리 외 의존성이 없는지 검사한다"

    doLast {
        val violations = listOf(configurations.compileClasspath.get(), configurations.runtimeClasspath.get())
            .flatMap { classpath ->
                val result = classpath.incoming.resolutionResult
                result.allComponents.filter { it != result.root }
            }
            .mapNotNull { component ->
                when (val id = component.id) {
                    is ModuleComponentIdentifier -> id.takeIf { it.group !in allowedGroups }?.let { "${it.group}:${it.module}" }
                    is ProjectComponentIdentifier -> "project ${id.projectPath}"
                    else -> id.displayName
                }
            }
            .distinct()

        if (violations.isNotEmpty()) {
            throw GradleException(
                "domain 모듈은 순수 Kotlin만 의존해야 한다 (ADR-0002). 허용되지 않은 의존성:\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyFrameworkIsolation)
}
