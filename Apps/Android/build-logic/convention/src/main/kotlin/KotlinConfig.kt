import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/** 모든 Kotlin 모듈이 같은 JDK 툴체인과 경고 규칙을 쓴다. */
internal fun Project.configureKotlin() {
    extensions.configure(KotlinProjectExtension::class.java) {
        jvmToolchain(versionInt("jvm-toolchain"))
    }
}
