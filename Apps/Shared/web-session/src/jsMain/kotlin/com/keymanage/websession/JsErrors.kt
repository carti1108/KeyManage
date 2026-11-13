package com.keymanage.websession

import com.keymanage.domain.server.ServerError
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.domain.vault.VaultTooLargeException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Kotlin 예외를 웹이 가를 수 있게 푼 값. 웹은 kind(예외 클래스의 simpleName)로 자기 오류 클래스를 고른다.
 * ServerError의 값은 status·code·retryAfterSeconds에, 그 밖의 예외가 가진 값은 detailJson에 담는다
 */
@JsExport
class JsErrorInfo internal constructor(
    val kind: String,
    val message: String?,
    val status: Int?,
    /** ServerErrorCode 이름(예: "AUTH_FAILED", "VAULT_CONFLICT") */
    val code: String?,
    val retryAfterSeconds: Int?,
    /**
     * 그 밖의 필드를 담은 JSON 객체 문자열. 없으면 null.
     * RevisionRollbackException: lastSeenRevision, serverRevision / VaultTooLargeException: sizeInBytes.
     * 원인(cause)이 있으면 cause: {kind, message, status, code, retryAfterSeconds, ...}를 같은 모양으로 중첩한다(VaultSetupException 등)
     */
    val detailJson: String?,
)

private const val MAX_CAUSE_DEPTH = 4

/** Kotlin 예외가 아니면(JS 자체 Error, 문자열 등) null */
@JsExport
fun errorInfo(error: Any?): JsErrorInfo? {
    val throwable = error as? Throwable ?: return null
    if (!isKotlinClass(throwable)) return null
    return toErrorInfo(throwable)
}

internal fun toErrorInfo(error: Throwable): JsErrorInfo {
    val server = error as? ServerError
    val detail = detailOf(error, MAX_CAUSE_DEPTH)
    return JsErrorInfo(
        kind = kindOf(error),
        message = error.message,
        status = server?.status,
        code = server?.code?.name,
        retryAfterSeconds = server?.retryAfterSeconds,
        detailJson = if (detail.isEmpty()) null else JsonObject(detail).toString(),
    )
}

private fun kindOf(error: Throwable): String = error::class.simpleName ?: "Throwable"

// 예외 종류별 값과 원인 체인. 원인은 kind·message·ServerError 값·그 원인의 detail을 한 객체로 합친다
private fun detailOf(error: Throwable, depth: Int): Map<String, JsonElement> {
    val fields = linkedMapOf<String, JsonElement>()
    when (error) {
        is RevisionRollbackException -> {
            fields["lastSeenRevision"] = JsonPrimitive(error.lastSeenRevision)
            fields["serverRevision"] = JsonPrimitive(error.serverRevision)
        }
        is VaultTooLargeException -> fields["sizeInBytes"] = JsonPrimitive(error.sizeInBytes)
    }
    val cause = error.cause
    if (cause != null && depth > 0) fields["cause"] = causeJson(cause, depth - 1)
    return fields
}

private fun causeJson(error: Throwable, depth: Int): JsonObject {
    val server = error as? ServerError
    val fields = linkedMapOf<String, JsonElement>(
        "kind" to JsonPrimitive(kindOf(error)),
        "message" to (error.message?.let(::JsonPrimitive) ?: JsonNull),
    )
    server?.let {
        fields["status"] = JsonPrimitive(it.status)
        fields["code"] = JsonPrimitive(it.code.name)
        it.retryAfterSeconds?.let { seconds -> fields["retryAfterSeconds"] = JsonPrimitive(seconds) }
    }
    fields.putAll(detailOf(error, depth))
    return JsonObject(fields)
}

// Kotlin/JS에서 Throwable은 JS Error이므로 is Throwable만으로는 웹의 자체 Error와 가를 수 없다. Kotlin 클래스에만 있는 메타데이터로 가른다
private fun isKotlinClass(error: Any): Boolean = js("typeof error.constructor.\$metadata\$ === 'object'").unsafeCast<Boolean>()
