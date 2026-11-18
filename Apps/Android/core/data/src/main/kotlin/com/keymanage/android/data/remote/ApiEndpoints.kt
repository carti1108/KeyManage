package com.keymanage.android.data.remote

/** HTTP 엔드포인트 하나. Scripts/check-api-contract.sh가 아래 선언을 docs/api-spec.md 표와 대조한다. */
internal data class Endpoint(val method: String, val path: String, val requiresToken: Boolean)

/** api-spec.md 엔드포인트 표(1:1). 추가·변경은 명세를 먼저 고친다. */
internal object ApiEndpoints {
    val GET_VAULT = Endpoint("GET", "/api/v1/vault", requiresToken = true)
    val SAVE_VAULT = Endpoint("POST", "/api/v1/vault", requiresToken = true)
    val PRELOGIN = Endpoint("POST", "/api/v1/auth/prelogin", requiresToken = false)
    val REGISTER = Endpoint("POST", "/api/v1/auth/register", requiresToken = false)
    val LOGIN = Endpoint("POST", "/api/v1/auth/login", requiresToken = false)
    val LOGOUT = Endpoint("POST", "/api/v1/auth/logout", requiresToken = true)
    val CHANGE_PASSWORD = Endpoint("POST", "/api/v1/auth/change-password", requiresToken = true)
    val DELETE_ACCOUNT = Endpoint("POST", "/api/v1/auth/delete-account", requiresToken = true)
}
