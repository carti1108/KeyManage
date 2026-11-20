// Kotlin·Android Gradle 플러그인은 build-logic(convention plugin)이 클래스패스에 올린다.
// 그래서 모듈은 버전이 붙는 alias(libs.plugins.*) 대신 id("...")로 적용한다
plugins {
    id("km.kmp.library") apply false
}
