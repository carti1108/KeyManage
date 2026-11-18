import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** 순수 Kotlin(JVM) 모듈. `:core:domain`처럼 Android에 의존하지 않는 계층에 쓴다(ADR-AND-001). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlin()
        dependencies {
            add("testImplementation", libs.findLibrary("kotlin-test").get())
            add("testImplementation", libs.findLibrary("junit4").get())
        }
    }
}
