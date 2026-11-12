package com.keymanage.domain.backup

import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.InvalidKdfParamsException
import com.keymanage.domain.crypto.UnsupportedPayloadException
import com.keymanage.domain.crypto.VaultDecryptException
import com.keymanage.domain.crypto.assertValidKdf
import com.keymanage.domain.crypto.deriveKeys
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.encoding.InvalidBase64Exception
import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.vault.MAX_PAYLOAD_BYTES
import com.keymanage.domain.vault.VAULT_SCHEMA_VERSION
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultTooLargeException
import com.keymanage.domain.vault.assertWithinLimit
import com.keymanage.domain.vault.formatTime
import com.keymanage.domain.vault.integerOrNull
import com.keymanage.domain.vault.parseVaultData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

// 암호화 백업 파일 쓰기·읽기와 비밀번호로 열기 (VLT-004, vault-spec 6장, ADR-0022)
// 원본: Apps/Web/src/domain/backup/backupFile.ts. 파일에는 암호문과 KDF 값만 담는다. 이메일·revision·세션 정보는 넣지 않는다

const val BACKUP_FORMAT: String = "keymanage-backup"

/** 이 앱이 아는 백업 형식 버전. 더 높으면 복원하지 않는다 */
const val BACKUP_FORMAT_VERSION: Int = 1

/** 파일의 머리 필드(format, kdf 등)와 들여쓰기 몫. 볼트 한도 직전 볼트를 내보낸 파일도 다시 읽게 한다 */
const val BACKUP_FILE_ALLOWANCE_BYTES: Int = 65_536

/** 파일 전체를 읽는 한도. 볼트 한도(VLT-001)는 vault 필드를 압축 JSON으로 다시 쓴 크기에 따로 적용한다 */
const val MAX_BACKUP_FILE_BYTES: Int = MAX_PAYLOAD_BYTES + BACKUP_FILE_ALLOWANCE_BYTES

private const val KDF_NAME = "PBKDF2-HMAC-SHA256"
private const val PAYLOAD_VERSION = 1
private const val PAYLOAD_CIPHER = "AES-256-GCM"

/** 형식이 맞지 않거나 5MB를 넘거나 KDF 값이 검증을 어김. 복호화를 시도하지 않는다 */
class NotABackupFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 이 앱보다 새 버전이 만든 백업(형식 버전, 암호화 볼트 버전, 볼트 스키마 버전) */
class NewerBackupVersionException(message: String) : Exception(message)

/** 비밀번호가 틀렸거나 파일이 손상됨. AES-GCM은 둘을 구분하지 못한다 */
class BackupDecryptException : Exception("백업 비밀번호가 틀렸거나 파일이 손상됨")

/** 저장할 백업 파일 */
data class BackupFile(val name: String, val text: String)

/** 사용자가 고른 파일. 크기를 먼저 보고 내용을 읽는다 */
class BackupFileSource(val size: Int, private val read: suspend () -> String) {
    suspend fun text(): String = read()
}

/** 백업 파일 저장·고르기 포트. 브라우저는 다운로드와 파일 선택 창, 모바일은 문서 선택기를 쓴다 */
interface BackupFiles {
    fun save(file: BackupFile)

    /** @return 고른 파일. 취소했으면 null */
    suspend fun pick(): BackupFileSource?
}

/** 기기의 현지 날짜. 공통 코드에는 시간대가 없어 플랫폼이 알려 준다 */
data class LocalDay(val year: Int, val month: Int, val day: Int)

fun interface LocalCalendar {
    fun dateOf(time: Instant): LocalDay
}

/** 형식을 확인한 백업. 아직 암호문이다 */
data class VaultBackup(val formatVersion: Int, val createdAt: String, val kdf: UnlockKdf, val vault: EncryptedVaultPayload)

/** keymanage-backup-YYYYMMDD.json. 날짜는 이 기기의 현지 날짜다 */
fun backupFileName(now: Instant, calendar: LocalCalendar): String {
    val (year, month, day) = calendar.dateOf(now)
    fun pad(value: Int) = value.toString().padStart(2, '0')
    return "keymanage-backup-$year${pad(month)}${pad(day)}.json"
}

// 원본 JSON.stringify(body, null, 2)와 같은 모양(두 칸 들여쓰기, "키": 값)
@OptIn(ExperimentalSerializationApi::class)
private val pretty = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** @param vault 지금 키로 새 IV를 써서 암호화한 볼트 */
fun createBackupFile(vault: EncryptedVaultPayload, kdf: UnlockKdf, now: Instant, calendar: LocalCalendar): BackupFile {
    val body = JsonObject(
        linkedMapOf(
            "format" to JsonPrimitive(BACKUP_FORMAT),
            "formatVersion" to JsonPrimitive(BACKUP_FORMAT_VERSION),
            "createdAt" to JsonPrimitive(formatTime(now)),
            "kdf" to JsonObject(linkedMapOf("name" to JsonPrimitive(KDF_NAME), "iterations" to JsonPrimitive(kdf.iterations), "salt" to JsonPrimitive(kdf.salt))),
            "vault" to vault.toJson(),
        ),
    )
    return BackupFile(backupFileName(now, calendar), pretty.encodeToString(JsonObject.serializer(), body))
}

/**
 * 크기를 먼저 확인하고, 넘으면 내용을 읽지 않는다
 * @throws NotABackupFileException, NewerBackupVersionException
 */
suspend fun readBackupFile(source: BackupFileSource): VaultBackup {
    if (source.size > MAX_BACKUP_FILE_BYTES) throw NotABackupFileException("파일이 ${MAX_BACKUP_FILE_BYTES}바이트를 넘음")
    val text = try {
        source.text()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw NotABackupFileException("파일을 읽지 못함", error)
    }
    return parseBackup(text)
}

/** @throws NotABackupFileException, NewerBackupVersionException */
fun parseBackup(text: String): VaultBackup {
    if (text.encodeToByteArray().size > MAX_BACKUP_FILE_BYTES) throw NotABackupFileException("파일이 ${MAX_BACKUP_FILE_BYTES}바이트를 넘음")
    val raw = try {
        Json.parseToJsonElement(text)
    } catch (error: SerializationException) {
        throw NotABackupFileException("JSON이 아님")
    }
    if (raw !is JsonObject || textOf(raw["format"]) != BACKUP_FORMAT) throw NotABackupFileException("format이 keymanage-backup이 아님")
    val formatVersion = positiveInteger(raw["formatVersion"]) ?: throw NotABackupFileException("formatVersion이 1 이상의 정수가 아님")
    if (formatVersion > BACKUP_FORMAT_VERSION) throw NewerBackupVersionException("모르는 백업 형식 버전: $formatVersion")
    val createdAt = textOf(raw["createdAt"]) ?: throw NotABackupFileException("createdAt이 문자열이 아님")
    return VaultBackup(formatVersion, createdAt, parseKdf(raw["kdf"]), parsePayload(raw["vault"]))
}

/**
 * 파일의 KDF 값과 비밀번호로 키를 만들어(SEC-001과 같은 전처리·검증) 볼트를 연다
 * @throws BackupDecryptException 비밀번호가 틀렸거나 파일이 손상됨
 * @throws NewerBackupVersionException 볼트 스키마 버전이 앱이 아는 것보다 높음
 * @throws com.keymanage.domain.crypto.InvalidPasswordException, com.keymanage.domain.vault.InvalidVaultDataException
 */
suspend fun openBackup(backup: VaultBackup, password: String): VaultData {
    val keys = deriveKeys(password, fromBase64(backup.kdf.salt), backup.kdf.iterations)
    // 인증 해시는 서버 로그인에만 쓴다. 복원에는 필요 없으니 바로 지운다
    keys.authHash.fill(0)
    val json = try {
        openVault(keys.encryptionKey, backup.vault)
    } catch (error: VaultDecryptException) {
        throw BackupDecryptException()
    } catch (error: UnsupportedPayloadException) {
        throw NotABackupFileException(error.message ?: "볼트 형식이 틀림", error)
    }
    // 새 버전 볼트는 지금 규칙으로 검사하면 형식 오류로 보일 수 있으니 버전부터 본다
    val version = schemaVersionOf(json)
    if (version != null && version > VAULT_SCHEMA_VERSION) throw NewerBackupVersionException("모르는 볼트 스키마 버전: $version")
    return parseVaultData(json)
}

private fun parseKdf(element: JsonElement?): UnlockKdf {
    val value = element as? JsonObject
    if (value == null || textOf(value["name"]) != KDF_NAME) throw NotABackupFileException("kdf.name이 ${KDF_NAME}이 아님")
    val iterationsElement = value["iterations"] as? JsonPrimitive
    val salt = textOf(value["salt"])
    if (iterationsElement == null || iterationsElement.isString || salt == null) throw NotABackupFileException("kdf 필드 타입이 틀림")
    // 소수·범위 밖 정수는 아래 검증에서 반복 횟수 오류가 된다
    val iterations = integerOrNull(iterationsElement) ?: throw NotABackupFileException("반복 횟수가 정수가 아님")
    try {
        assertValidKdf(decode(salt, "kdf.salt"), iterations)
    } catch (error: InvalidKdfParamsException) {
        throw NotABackupFileException(error.message ?: "KDF 값이 틀림", error)
    }
    return UnlockKdf(salt, iterations)
}

private fun parsePayload(element: JsonElement?): EncryptedVaultPayload {
    val value = element as? JsonObject ?: throw NotABackupFileException("vault가 객체가 아님")
    val version = positiveInteger(value["version"]) ?: throw NotABackupFileException("vault.version이 1 이상의 정수가 아님")
    if (version > PAYLOAD_VERSION) throw NewerBackupVersionException("모르는 볼트 암호화 버전: $version")
    if (textOf(value["cipher"]) != PAYLOAD_CIPHER) throw NotABackupFileException("vault.cipher가 ${PAYLOAD_CIPHER}이 아님")
    val (iv, ciphertext, tag) = listOf("iv", "ciphertext", "tag").map { name ->
        val field = textOf(value[name]) ?: throw NotABackupFileException("vault.${name}이 문자열이 아님")
        decode(field, "vault.$name")
        field
    }
    val payload = EncryptedVaultPayload(PAYLOAD_VERSION, PAYLOAD_CIPHER, iv, ciphertext, tag)
    // 볼트 한도는 저장할 때와 같은 압축 JSON 크기로 잰다
    try {
        assertWithinLimit(payload)
    } catch (error: VaultTooLargeException) {
        throw NotABackupFileException(error.message ?: "볼트가 너무 큼", error)
    }
    return payload
}

private fun decode(text: String, name: String): ByteArray = try {
    fromBase64(text)
} catch (error: InvalidBase64Exception) {
    throw NotABackupFileException("${name}이 표준 Base64가 아님")
}

private fun schemaVersionOf(json: String): Int? = try {
    ((Json.parseToJsonElement(json) as? JsonObject)?.get("version") as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()?.toInt()
} catch (error: SerializationException) {
    null
}

private fun positiveInteger(element: JsonElement?): Int? = integerOrNull(element)?.takeIf { it >= 1 }

private fun textOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
