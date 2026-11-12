package com.keymanage.domain.device

import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.MAX_ITERATIONS
import com.keymanage.domain.crypto.MIN_ITERATIONS
import com.keymanage.domain.crypto.SALT_BYTES
import com.keymanage.domain.encoding.InvalidBase64Exception
import com.keymanage.domain.encoding.fromBase64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// 기기에 저장하는 한 계정의 기록 (ADR-0019, VLT-003). 원본: Apps/Web/src/domain/device/deviceRecord.ts
// 비밀이 아니거나(이메일, KDF 값, revision) 암호화된 값(볼트)만 담는다. 토큰·키·인증 해시·평문은 담지 않는다

/** 기기 저장소를 쓰거나 읽지 못함. 원본: deviceRecord.ts의 DeviceStorageError */
class DeviceStorageException(cause: Throwable? = null) : Exception("기기 저장소를 쓰지 못함", cause)

/** 기록 형식 버전. 형식을 바꾸면 올리고 이전 기록을 옮기는 절차를 둔다 */
const val DEVICE_RECORD_VERSION: Int = 1

data class DeviceRecord(
    val email: String,
    val kdf: UnlockKdf,
    /** 마지막 볼트. 동기화 대기 중이면 대기 내용을 담은 볼트다 */
    val payload: EncryptedVaultPayload,
    /** payload가 바탕으로 삼은 서버 revision */
    val revision: Int,
    /** 되돌림 감지 기준 (VLT-003) */
    val lastSeenRevision: Int,
    val pendingSync: Boolean,
)

/**
 * 기기 저장소 포트. 기록은 [encodeDeviceRecord]가 만든 JSON 문자열이고, 구현은 저장 위치만 맡는다.
 * 웹 IndexedDB 구현(device-store 모듈)은 원본과 같이 객체로 저장한다
 */
interface DeviceRecordStore {
    /** 기록이 없으면 null. 읽은 값은 [parseDeviceRecord]로 확인한다 */
    suspend fun load(): String?
    suspend fun save(recordJson: String)
    suspend fun clear()
}

/** 저장 형식. 키 순서는 원본 객체와 같다 */
fun encodeDeviceRecord(record: DeviceRecord): String = JsonObject(
    linkedMapOf(
        "version" to JsonPrimitive(DEVICE_RECORD_VERSION),
        "email" to JsonPrimitive(record.email),
        "kdf" to JsonObject(linkedMapOf("salt" to JsonPrimitive(record.kdf.salt), "iterations" to JsonPrimitive(record.kdf.iterations))),
        "payload" to record.payload.toJson(),
        "revision" to JsonPrimitive(record.revision),
        "lastSeenRevision" to JsonPrimitive(record.lastSeenRevision),
        "pendingSync" to JsonPrimitive(record.pendingSync),
    ),
).toString()

/** 기기에서 읽은 값을 확인한다. 없거나, 다른 형식 버전이거나, 규격에 맞지 않으면 null (쓰지 않는다) */
fun parseDeviceRecord(json: String?): DeviceRecord? {
    val value = json?.let {
        try {
            Json.parseToJsonElement(it)
        } catch (error: SerializationException) {
            null
        }
    } as? JsonObject ?: return null
    if (integer(value["version"]) != DEVICE_RECORD_VERSION) return null
    val email = text(value["email"]) ?: return null
    val kdf = kdfOf(value["kdf"]) ?: return null
    val payload = payloadOf(value["payload"]) ?: return null
    val revision = revisionOf(value["revision"]) ?: return null
    val lastSeenRevision = revisionOf(value["lastSeenRevision"]) ?: return null
    val pendingSync = (value["pendingSync"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: return null
    return DeviceRecord(email, kdf, payload, revision, lastSeenRevision, pendingSync)
}

// 키 유도가 받아들이지 않는 값이면 잠금 화면에서 빠져나올 수 없으므로 읽을 때 거른다 (SEC-001)
private fun kdfOf(element: JsonElement?): UnlockKdf? {
    val value = element as? JsonObject ?: return null
    val salt = text(value["salt"])?.takeIf(::isSalt) ?: return null
    val iterations = integer(value["iterations"])?.takeIf { it in MIN_ITERATIONS..MAX_ITERATIONS } ?: return null
    return UnlockKdf(salt, iterations)
}

// 헤더 값의 규격은 복호화할 때 openVault가 확인한다. 여기서는 모양만 본다
private fun payloadOf(element: JsonElement?): EncryptedVaultPayload? {
    val value = element as? JsonObject ?: return null
    val version = integer(value["version"]) ?: return null
    return EncryptedVaultPayload(
        version,
        text(value["cipher"]) ?: return null,
        text(value["iv"]) ?: return null,
        text(value["ciphertext"]) ?: return null,
        text(value["tag"]) ?: return null,
    )
}

private fun revisionOf(element: JsonElement?): Int? = integer(element)?.takeIf { it >= 0 }

private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

// 원본 Number.isSafeInteger처럼 숫자 정수만 받는다. 문자열 "4"와 1.5는 받지 않는다
private fun integer(element: JsonElement?): Int? {
    val value = (element as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
    val number = value.content.toDoubleOrNull() ?: return null
    if (number != kotlin.math.floor(number) || number > Int.MAX_VALUE || number < Int.MIN_VALUE) return null
    return number.toInt()
}

private fun isSalt(value: String): Boolean = try {
    fromBase64(value).size == SALT_BYTES
} catch (error: InvalidBase64Exception) {
    false
}
