package com.keymanage.domain.backup

import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.InvalidPasswordException
import com.keymanage.domain.crypto.MAX_ITERATIONS
import com.keymanage.domain.crypto.MIN_ITERATIONS
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.crypto.text
import com.keymanage.domain.crypto.withLoneSurrogate
import com.keymanage.domain.vault.InvalidVaultDataException
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.MAX_PAYLOAD_BYTES
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.emptyVault
import com.keymanage.domain.vault.encodeVaultData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// 원본: Apps/Web/src/domain/backup/backupFile.test.ts (VLT-004, vault-spec 6장, ADR-0022)
private val NOW = Instant.parse("2026-11-01T12:00:00Z")
private val vector = Vectors.keyCases[0]
private val PASSWORD = vector.text("password")
private val KDF = UnlockKdf(vector.text("salt"), Vectors.iterations)
private val fields = ItemFields("GitHub", "octocat", "pw-1")

private suspend fun key(): EncryptionKey = Vectors.keysOf(vector.text("name")).encryptionKey

private suspend fun backupOf(data: VaultData): String = createBackupFile(sealVault(key(), encodeVaultData(data)), KDF, NOW, utcCalendar).text

private val utcCalendar = LocalCalendar { time ->
    val days = time.epochSeconds.floorDiv(86_400)
    // 1970-01-01부터 날짜 계산(테스트 전용, UTC)
    val z = days + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    LocalDay(year, month, day)
}

/** 올바른 백업 파일 JSON을 고쳐 쓴다 */
private suspend fun editedBackup(edit: (MutableMap<String, JsonElement>) -> Unit): String {
    val raw = Json.parseToJsonElement(backupOf(emptyVault(NOW))).jsonObject.toMutableMap()
    edit(raw)
    return JsonObject(raw).toString()
}

private fun MutableMap<String, JsonElement>.editObject(field: String, edit: (MutableMap<String, JsonElement>) -> Unit) {
    val inner = getValue(field).jsonObject.toMutableMap()
    edit(inner)
    this[field] = JsonObject(inner)
}

private fun compactSize(payload: EncryptedVaultPayload) = payload.toJson().toString().encodeToByteArray().size

/** 압축 JSON 크기가 limit 이하인 가장 큰 페이로드. Base64 필드라 4바이트 단위로 늘어난다 */
private fun largestPayloadWithin(limit: Int): EncryptedVaultPayload {
    val base = EncryptedVaultPayload(1, "AES-256-GCM", "AAAAAAAAAAAAAAAA", "", "AAAAAAAAAAAAAAAAAAAAAA==")
    val room = limit - compactSize(base)
    return base.copy(ciphertext = "A".repeat(room - room % 4))
}

private fun source(text: String, size: Int = text.encodeToByteArray().size) = BackupFileSource(size) { text }

class BackupFileNameTest {
    @Test
    fun usesLocalDateOfExportingDevice() {
        val fixed = LocalCalendar { LocalDay(2026, 1, 5) }
        assertEquals("keymanage-backup-20260105.json", backupFileName(NOW, fixed))
        assertEquals("keymanage-backup-20261101.json", backupFileName(NOW, LocalCalendar { LocalDay(2026, 11, 1) }))
    }
}

class CreateBackupFileTest {
    @Test
    fun containsOnlySpecFields_noEmailRevisionOrPlaintext() = runTest(timeout = 5.minutes) {
        val payload = sealVault(key(), encodeVaultData(addItem(emptyVault(NOW), fields, NOW, id = "a")))

        val file = createBackupFile(payload, KDF, NOW, utcCalendar)
        val raw = Json.parseToJsonElement(file.text).jsonObject

        assertEquals(backupFileName(NOW, utcCalendar), file.name)
        assertEquals(listOf("createdAt", "format", "formatVersion", "kdf", "vault"), raw.keys.sorted())
        listOf("GitHub", "octocat", "pw-1", "revision", "@").forEach { assertFalse(it in file.text, it) }
    }

    // 원본은 JSON.stringify(body, null, 2)로 쓴다. 아래 기대값은 Node가 같은 객체로 만든 결과다
    @Test
    fun prettyPrintsExactlyLikeWeb() {
        val file = createBackupFile(EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn"), UnlockKdf("AAAAAAAAAAAAAAAAAAAAAA==", 600_000), NOW, utcCalendar)

        assertEquals(
            """
            {
              "format": "keymanage-backup",
              "formatVersion": 1,
              "createdAt": "2026-11-01T12:00:00Z",
              "kdf": {
                "name": "PBKDF2-HMAC-SHA256",
                "iterations": 600000,
                "salt": "AAAAAAAAAAAAAAAAAAAAAA=="
              },
              "vault": {
                "version": 1,
                "cipher": "AES-256-GCM",
                "iv": "aXY=",
                "ciphertext": "Y3Q=",
                "tag": "dGFn"
              }
            }
            """.trimIndent(),
            file.text,
        )
    }
}

class ParseBackupTest {
    @Test
    fun validFileReturnsKdfAndPayload() = runTest(timeout = 5.minutes) {
        val text = backupOf(emptyVault(NOW))

        val backup = readBackupFile(source(text))

        assertEquals(BACKUP_FORMAT_VERSION, backup.formatVersion)
        assertEquals(KDF, backup.kdf)
        assertEquals("2026-11-01T12:00:00Z", backup.createdAt)
        assertEquals(Json.parseToJsonElement(text).jsonObject.getValue("vault"), backup.vault.toJson())
    }

    @Test
    fun oversizedFileIsRejectedWithoutReading_exactLimitIsRead() = runTest(timeout = 5.minutes) {
        assertEquals(65_536, BACKUP_FILE_ALLOWANCE_BYTES)
        assertEquals(MAX_PAYLOAD_BYTES + BACKUP_FILE_ALLOWANCE_BYTES, MAX_BACKUP_FILE_BYTES)
        var read = false
        val huge = BackupFileSource(MAX_BACKUP_FILE_BYTES + 1) { read = true; "" }

        assertFailsWith<NotABackupFileException> { readBackupFile(huge) }
        assertFalse(read)

        readBackupFile(source(backupOf(emptyVault(NOW)), MAX_BACKUP_FILE_BYTES))
    }

    @Test
    fun textOverLimitInUtf8IsRejected() {
        assertFailsWith<NotABackupFileException> { parseBackup("가".repeat(MAX_BACKUP_FILE_BYTES / 3 + 1)) }
    }

    @Test
    fun vaultLimitAppliesToCompactJsonOfVaultField() {
        val atLimit = largestPayloadWithin(MAX_PAYLOAD_BYTES)
        val overLimit = atLimit.copy(ciphertext = atLimit.ciphertext + "AAAA")
        assertTrue(compactSize(overLimit) > MAX_PAYLOAD_BYTES)

        val accepted = createBackupFile(atLimit, KDF, NOW, utcCalendar).text
        // 들여쓰기·머리 필드 때문에 파일 자체는 볼트 한도보다 크다
        assertTrue(accepted.encodeToByteArray().size > MAX_PAYLOAD_BYTES)
        assertEquals(atLimit, parseBackup(accepted).vault)
        assertFailsWith<NotABackupFileException> { parseBackup(createBackupFile(overLimit, KDF, NOW, utcCalendar).text) }
    }

    @Test
    fun nearLimitVaultRoundTripsThroughBackupFile() = runTest(timeout = 5.minutes) {
        // 실제 키로 암호화한 페이로드가 볼트 한도 바로 아래가 되도록 메모 길이를 맞춘다
        fun withNotes(length: Int) = addItem(emptyVault(NOW), fields.copy(notes = "x".repeat(length)), NOW, id = "big")
        var length = MAX_PAYLOAD_BYTES * 3 / 4 - 1_000
        var data: VaultData
        var payload: EncryptedVaultPayload
        while (true) {
            data = withNotes(length)
            payload = sealVault(key(), encodeVaultData(data))
            val room = MAX_PAYLOAD_BYTES - compactSize(payload)
            if (room in 0 until 8) break
            length += (room * 3 / 4).takeIf { it != 0 } ?: if (room < 0) -1 else 1
        }
        val file = createBackupFile(payload, KDF, NOW, utcCalendar)

        assertEquals(data, openBackup(readBackupFile(source(file.text)), PASSWORD))
    }

    @Test
    fun notJsonOrNotObjectIsRejected() = runTest {
        listOf("", "not json", "[]", "null", "\"keymanage-backup\"").forEach {
            assertFailsWith<NotABackupFileException>(it) { readBackupFile(source(it)) }
        }
    }

    @Test
    fun otherFormatIsRejected() = runTest(timeout = 5.minutes) {
        listOf<JsonElement?>(JsonPrimitive("keymanage-vault"), null, JsonPrimitive(1)).forEach { format ->
            val text = editedBackup { if (format == null) it.remove("format") else it["format"] = format }
            assertFailsWith<NotABackupFileException>("$format") { parseBackup(text) }
        }
        assertEquals("keymanage-backup", BACKUP_FORMAT)
    }

    @Test
    fun missingOrWrongTypedFieldsAreRejected() = runTest(timeout = 5.minutes) {
        val edits: List<Pair<String, (MutableMap<String, JsonElement>) -> Unit>> = listOf(
            "formatVersion 없음" to { it.remove("formatVersion") },
            "formatVersion 문자열" to { it["formatVersion"] = JsonPrimitive("1") },
            "formatVersion 0" to { it["formatVersion"] = JsonPrimitive(0) },
            "formatVersion 소수" to { it["formatVersion"] = JsonPrimitive(1.5) },
            "createdAt 없음" to { it.remove("createdAt") },
            "createdAt 숫자" to { it["createdAt"] = JsonPrimitive(0) },
            "kdf 없음" to { it.remove("kdf") },
            "kdf 배열" to { it["kdf"] = JsonArray(emptyList()) },
            "kdf.name 다름" to { raw -> raw.editObject("kdf") { it["name"] = JsonPrimitive("Argon2id") } },
            "kdf.iterations 문자열" to { raw -> raw.editObject("kdf") { it["iterations"] = JsonPrimitive("600000") } },
            "kdf.salt 없음" to { raw -> raw.editObject("kdf") { it.remove("salt") } },
            "kdf.salt Base64 아님" to { raw -> raw.editObject("kdf") { it["salt"] = JsonPrimitive("AAECAwQFBgcICQoLDA0ODw") } },
            "vault 없음" to { it.remove("vault") },
            "vault.iv 없음" to { raw -> raw.editObject("vault") { it.remove("iv") } },
            "vault.ciphertext 숫자" to { raw -> raw.editObject("vault") { it["ciphertext"] = JsonPrimitive(1) } },
            "vault.tag Base64 아님" to { raw -> raw.editObject("vault") { it["tag"] = JsonPrimitive("***") } },
            "vault.cipher 다름" to { raw -> raw.editObject("vault") { it["cipher"] = JsonPrimitive("AES-128-CBC") } },
            "vault.version 문자열" to { raw -> raw.editObject("vault") { it["version"] = JsonPrimitive("1") } },
        )
        edits.forEach { (name, edit) ->
            val text = editedBackup(edit)
            assertFailsWith<NotABackupFileException>(name) { parseBackup(text) }
        }
    }

    @Test
    fun kdfOutsideSec001IsRejected_boundariesAccepted() = runTest(timeout = 5.minutes) {
        val edits: List<Pair<String, (MutableMap<String, JsonElement>) -> Unit>> = listOf(
            "반복 횟수 하한 미만" to { raw -> raw.editObject("kdf") { it["iterations"] = JsonPrimitive(MIN_ITERATIONS - 1) } },
            "반복 횟수 상한 초과" to { raw -> raw.editObject("kdf") { it["iterations"] = JsonPrimitive(MAX_ITERATIONS + 1) } },
            "salt 15바이트" to { raw -> raw.editObject("kdf") { it["salt"] = JsonPrimitive("AAECAwQFBgcICQoLDA0O") } },
            "salt 17바이트" to { raw -> raw.editObject("kdf") { it["salt"] = JsonPrimitive("AAECAwQFBgcICQoLDA0ODxA=") } },
        )
        edits.forEach { (name, edit) -> assertFailsWith<NotABackupFileException>(name) { parseBackup(editedBackup(edit)) } }
        listOf(MIN_ITERATIONS, MAX_ITERATIONS).forEach { iterations ->
            val text = editedBackup { raw -> raw.editObject("kdf") { it["iterations"] = JsonPrimitive(iterations) } }
            assertEquals(iterations, parseBackup(text).kdf.iterations)
        }
    }

    @Test
    fun higherFormatOrPayloadVersionIsNewerVersion() = runTest(timeout = 5.minutes) {
        assertFailsWith<NewerBackupVersionException> { parseBackup(editedBackup { it["formatVersion"] = JsonPrimitive(2) }) }
        assertFailsWith<NewerBackupVersionException> {
            parseBackup(editedBackup { raw -> raw.editObject("vault") { it["version"] = JsonPrimitive(2) } })
        }
    }

    @Test
    fun unknownTopLevelFieldsAreIgnored() = runTest(timeout = 5.minutes) {
        assertEquals(1, parseBackup(editedBackup { it["note"] = JsonPrimitive("x") }).formatVersion)
    }
}

class OpenBackupTest {
    @Test
    fun correctPasswordOpensVault_preprocessedLikeMasterPassword() = runTest(timeout = 5.minutes) {
        val data = addItem(emptyVault(NOW), fields, NOW, id = "a")
        val backup = parseBackup(backupOf(data))

        assertEquals(data, openBackup(backup, "  $PASSWORD "))
    }

    @Test
    fun wrongPasswordIsBackupDecryptError() = runTest(timeout = 5.minutes) {
        val backup = parseBackup(backupOf(emptyVault(NOW)))

        assertFailsWith<BackupDecryptException> { openBackup(backup, "wrong password") }
    }

    @Test
    fun tamperedTagIsBackupDecryptError() = runTest(timeout = 5.minutes) {
        val flipped = editedBackup { raw ->
            raw.editObject("vault") {
                val tag = (it.getValue("tag") as JsonPrimitive).content
                it["tag"] = JsonPrimitive((if (tag[0] == 'A') "B" else "A") + tag.drop(1))
            }
        }
        assertFailsWith<BackupDecryptException> { openBackup(parseBackup(flipped), PASSWORD) }

        val shortTag = editedBackup { raw -> raw.editObject("vault") { it["tag"] = JsonPrimitive("AAAA") } }
        assertFailsWith<BackupDecryptException> { openBackup(parseBackup(shortTag), PASSWORD) }
    }

    @Test
    fun newerVaultSchemaIsNewerVersion() = runTest(timeout = 5.minutes) {
        val newer = """{"version":2,"updatedAt":"2026-11-01T00:00:00Z","items":[{"id":"x","future":true}]}"""
        val backup = parseBackup(createBackupFile(sealVault(key(), newer), KDF, NOW, utcCalendar).text)

        assertFailsWith<NewerBackupVersionException> { openBackup(backup, PASSWORD) }
    }

    @Test
    fun decryptedButInvalidVaultIsFormatError() = runTest(timeout = 5.minutes) {
        val broken = """{"version":1,"updatedAt":"2026-11-01T00:00:00Z","items":"not-array"}"""
        val backup = parseBackup(createBackupFile(sealVault(key(), broken), KDF, NOW, utcCalendar).text)

        assertFailsWith<InvalidVaultDataException> { openBackup(backup, PASSWORD) }
    }

    @Test
    fun brokenUnicodePasswordIsSameErrorAsMasterPassword() = runTest(timeout = 5.minutes) {
        val backup = parseBackup(backupOf(emptyVault(NOW)))

        assertFailsWith<InvalidPasswordException> { openBackup(backup, withLoneSurrogate("ab", '\uD83D', "cd")) }
    }
}
