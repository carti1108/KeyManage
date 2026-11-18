import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/** Android 모듈 공통: SDK 버전과 단위 테스트 설정. */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = versionInt("compile-sdk")
        defaultConfig.minSdk = versionInt("min-sdk")
        testOptions.unitTests.isReturnDefaultValues = true
    }
}
