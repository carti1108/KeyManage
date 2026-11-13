package com.keymanage.websession

import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.VaultSetupException
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultTooLargeException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun jsError(): Any = js("new Error('boom')").unsafeCast<Any>()

class ErrorInfoTest {
    @Test
    fun nonKotlinValuesAreNull() {
        assertNull(errorInfo(null))
        assertNull(errorInfo("text"))
        assertNull(errorInfo(jsError()))
    }

    @Test
    fun plainExceptionHasOnlyKindAndMessage() {
        val info = assertNotNull(errorInfo(SessionExpiredException()))
        assertEquals("SessionExpiredException", info.kind)
        assertEquals("세션이 만료됨", info.message)
        assertNull(info.status)
        assertNull(info.code)
        assertNull(info.retryAfterSeconds)
        assertNull(info.detailJson)
    }

    @Test
    fun serverErrorExposesStatusCodeAndRetryAfter() {
        val info = assertNotNull(errorInfo(ServerError(409, ServerErrorCode.VAULT_CONFLICT)))
        assertEquals("ServerError", info.kind)
        assertEquals(409, info.status)
        assertEquals("VAULT_CONFLICT", info.code)
        assertNull(info.retryAfterSeconds)

        assertEquals(30, errorInfo(ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS, 30))?.retryAfterSeconds)
    }

    @Test
    fun networkErrorKind() {
        assertEquals("NetworkError", errorInfo(NetworkError())?.kind)
    }

    @Test
    fun revisionRollbackDetail() {
        val detail = Json.parseToJsonElement(assertNotNull(errorInfo(RevisionRollbackException(5, 3))?.detailJson)).jsonObject
        assertEquals(5, detail.getValue("lastSeenRevision").jsonPrimitive.int)
        assertEquals(3, detail.getValue("serverRevision").jsonPrimitive.int)
    }

    @Test
    fun vaultTooLargeDetail() {
        val detail = Json.parseToJsonElement(assertNotNull(errorInfo(VaultTooLargeException(6_000_000))?.detailJson)).jsonObject
        assertEquals(6_000_000, detail.getValue("sizeInBytes").jsonPrimitive.int)
    }

    @Test
    fun causeIsNestedWithKindAndServerFields() {
        val info = assertNotNull(errorInfo(VaultSetupException(ServerError(503, ServerErrorCode.INTERNAL_ERROR))))
        assertEquals("VaultSetupException", info.kind)
        val cause = Json.parseToJsonElement(assertNotNull(info.detailJson)).jsonObject.getValue("cause").jsonObject
        assertEquals("ServerError", cause.getValue("kind").jsonPrimitive.content)
        assertEquals(503, cause.getValue("status").jsonPrimitive.int)
        assertEquals("INTERNAL_ERROR", cause.getValue("code").jsonPrimitive.content)
    }
}
