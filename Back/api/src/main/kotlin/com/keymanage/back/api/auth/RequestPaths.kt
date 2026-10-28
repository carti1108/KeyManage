package com.keymanage.back.api.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.util.UrlPathHelper
import java.net.URI

/**
 * 필터가 경로를 판단할 때 쓰는 정규화된 경로.
 * 원본 URI(`/api/v1;x=1/vault`, `//api/v1/vault`, `/api/v1/auth/../vault`)는 Spring이 같은 핸들러로 보내도
 * 문자열로는 달라 보이므로, 디코딩·세미콜론 제거·중복 슬래시·`.`/`..` 정리를 거친 뒤 비교한다.
 */
object RequestPaths {

    fun normalized(request: HttpServletRequest): String {
        // 디코딩, 세미콜론 내용 제거, 중복 슬래시 정리를 Spring MVC와 같은 방식으로 한다
        val path = UrlPathHelper.defaultInstance.getPathWithinApplication(request)
        return runCatching { URI(null, null, path, null).normalize().path }.getOrDefault(path)
    }
}
