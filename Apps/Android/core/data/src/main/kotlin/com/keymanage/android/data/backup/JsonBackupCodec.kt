package com.keymanage.android.data.backup

import com.keymanage.android.domain.backup.BackupCodec
import com.keymanage.android.domain.backup.BackupError
import com.keymanage.android.domain.backup.BackupFile
import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.sync.SaveVaultUseCase
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** 백업 파일 JSON(vault-spec 6장). 크기 한도와 머리 필드를 검사하고, KDF 값의 범위 검사는 도메인이 한다. */
class JsonBackupCodec : BackupCodec {
    override fun encode(file: BackupFile): ByteArray {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdAt", file.createdAt.truncatedTo(ChronoUnit.SECONDS).toString())
            .put("kdf", JSONObject().put("name", KDF_NAME).put("iterations", file.kdfIterations).put("salt", file.kdfSalt))
            .put("vault", JSONObject(file.vault.canonicalJson()))
        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    override fun decode(bytes: ByteArray): BackupFile {
        if (bytes.size > MAX_FILE_BYTES) throw BackupError.NotBackupFile("파일이 ${MAX_FILE_BYTES}바이트를 넘음")
        val root = try {
            JSONTokener(bytes.toString(Charsets.UTF_8)).nextValue() as? JSONObject
        } catch (error: JSONException) {
            null
        } ?: throw BackupError.NotBackupFile("JSON 객체가 아님")
        if (root.opt("format") != FORMAT) throw BackupError.NotBackupFile("format이 $FORMAT 이 아님")
        val formatVersion = root.opt("formatVersion") as? Int
        if (formatVersion == null || formatVersion < 1) throw BackupError.NotBackupFile("formatVersion이 1 이상의 정수가 아님")
        if (formatVersion > FORMAT_VERSION) throw BackupError.NewerVersion("모르는 백업 형식 버전: $formatVersion")

        val kdf = root.opt("kdf") as? JSONObject ?: throw BackupError.NotBackupFile("kdf가 객체가 아님")
        if (kdf.opt("name") != KDF_NAME) throw BackupError.NotBackupFile("kdf.name이 $KDF_NAME 이 아님")
        val vault = root.opt("vault") as? JSONObject ?: throw BackupError.NotBackupFile("vault가 객체가 아님")
        val payload = EncryptedVaultPayload(
            version = vault.requireInt("version"),
            cipher = vault.requireString("cipher"),
            iv = vault.requireString("iv"),
            ciphertext = vault.requireString("ciphertext"),
            tag = vault.requireString("tag"),
        )
        val vaultSize = payload.canonicalJson().toByteArray(Charsets.UTF_8).size
        if (vaultSize > SaveVaultUseCase.MAX_PAYLOAD_BYTES) throw BackupError.NotBackupFile("vault 필드가 ${vaultSize}바이트")
        return BackupFile(
            createdAt = parseTime(root.requireString("createdAt")),
            kdfIterations = kdf.requireInt("iterations"),
            kdfSalt = kdf.requireString("salt"),
            vault = payload,
        )
    }

    private fun parseTime(text: String): Instant = try {
        OffsetDateTime.parse(text).toInstant()
    } catch (error: DateTimeParseException) {
        throw BackupError.NotBackupFile("createdAt 형식 오류", error)
    }

    private fun JSONObject.requireInt(key: String): Int = opt(key) as? Int ?: throw BackupError.NotBackupFile("$key 정수 아님")
    private fun JSONObject.requireString(key: String): String = opt(key) as? String ?: throw BackupError.NotBackupFile("$key 문자열 아님")

    companion object {
        const val FORMAT = "keymanage-backup"
        const val FORMAT_VERSION = 1
        const val KDF_NAME = "PBKDF2-HMAC-SHA256"

        /** 머리 필드·들여쓰기 몫의 여유(VLT-004 값 표). */
        const val FILE_ALLOWANCE_BYTES = 65_536

        /** 파일 전체 상한: 볼트 5MB + 64KB. */
        const val MAX_FILE_BYTES = SaveVaultUseCase.MAX_PAYLOAD_BYTES + FILE_ALLOWANCE_BYTES
    }
}
