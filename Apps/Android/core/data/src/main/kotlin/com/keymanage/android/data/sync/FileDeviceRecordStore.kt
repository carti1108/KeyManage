package com.keymanage.android.data.sync

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.DeviceRecord
import com.keymanage.android.domain.sync.DeviceRecordStore
import com.keymanage.android.domain.sync.DeviceStorageError
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 기기 기록을 JSON 파일 하나에 둔다(VLT-003). 임시 파일에 쓴 뒤 이름을 바꿔 교체하므로 쓰다 끊겨도 이전 기록이 남는다.
 * 앱은 백업에서 빠지는 `noBackupFilesDir` 아래 파일을 넘긴다.
 */
class FileDeviceRecordStore(private val file: File) : DeviceRecordStore {
    override fun read(): DeviceRecord? {
        if (!file.exists()) return null
        return try {
            decode(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (error: IOException) {
            throw DeviceStorageError("기기 기록을 읽지 못했다", error)
        } catch (error: JSONException) {
            throw DeviceStorageError("기기 기록 형식 오류", error)
        }
    }

    override fun write(record: DeviceRecord) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText(encode(record).toString(), Charsets.UTF_8)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: IOException) {
            temp.delete()
            throw DeviceStorageError("기기 기록을 쓰지 못했다", error)
        }
    }

    override fun clear() {
        try {
            Files.deleteIfExists(file.toPath())
        } catch (error: IOException) {
            throw DeviceStorageError("기기 기록을 지우지 못했다", error)
        }
    }

    private fun encode(record: DeviceRecord) = JSONObject()
        .put("email", record.account.email)
        .put("kdfIterations", record.account.kdfIterations)
        .put("kdfSalt", record.account.kdfSalt)
        .put("payload", JSONObject(record.payload.canonicalJson()))
        .put("baseRevision", record.baseRevision)
        .put("lastSeenRevision", record.lastSeenRevision)
        .put("hasPending", record.hasPending)

    private fun decode(json: JSONObject): DeviceRecord {
        val payload = json.getJSONObject("payload")
        return DeviceRecord(
            account = DeviceAccount(json.getString("email"), json.getInt("kdfIterations"), json.getString("kdfSalt")),
            payload = EncryptedVaultPayload(
                version = payload.getInt("version"),
                cipher = payload.getString("cipher"),
                iv = payload.getString("iv"),
                ciphertext = payload.getString("ciphertext"),
                tag = payload.getString("tag"),
            ),
            baseRevision = json.getInt("baseRevision"),
            lastSeenRevision = json.getInt("lastSeenRevision"),
            hasPending = json.getBoolean("hasPending"),
        )
    }
}
