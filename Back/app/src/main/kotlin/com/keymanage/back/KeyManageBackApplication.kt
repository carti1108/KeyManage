package com.keymanage.back

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// com.keymanage.back 아래(domain, infrastructure, api 모듈 포함)의 컴포넌트를 찾아 조립한다 (ADR-0002)
@SpringBootApplication
class KeyManageBackApplication

fun main(args: Array<String>) {
    runApplication<KeyManageBackApplication>(*args)
}
