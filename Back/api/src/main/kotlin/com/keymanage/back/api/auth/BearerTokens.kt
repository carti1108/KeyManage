package com.keymanage.back.api.auth

/** `Authorization: Bearer <토큰>` 헤더에서 토큰을 꺼낸다. 형식이 맞지 않으면 null (api-spec 1장) */
object BearerTokens {

    private const val SCHEME = "Bearer"

    fun extract(authorization: String?): String? {
        val parts = authorization?.trim()?.split(" ", limit = 2) ?: return null
        if (parts.size != 2 || !parts[0].equals(SCHEME, ignoreCase = true)) return null
        return parts[1].trim().ifEmpty { null }
    }
}
