package com.keymanage.android.data.vault

import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultFormatError
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultRules
import com.keymanage.android.domain.vault.VaultSerializer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * vault-spec 4장 JSON 코덱. Android 플랫폼의 org.json을 쓴다.
 * 모르는 필드는 원래 JSON 조각으로 [VaultData.extras]·[VaultItem.extras]에 담았다가 그대로 다시 쓴다.
 */
class JsonVaultSerializer : VaultSerializer {

    override fun decode(bytes: ByteArray): VaultData {
        val root = try {
            JSONTokener(bytes.toString(Charsets.UTF_8)).nextValue() as? JSONObject
        } catch (error: JSONException) {
            null
        } ?: throw VaultFormatError("볼트가 JSON 객체가 아니다")

        val itemsArray = root.opt(ITEMS) as? JSONArray ?: throw VaultFormatError("items가 배열이 아니다")
        val vault = VaultData(
            version = root.requireInt(VERSION),
            updatedAt = root.requireTime(UPDATED_AT),
            items = (0 until itemsArray.length()).map { index ->
                val item = itemsArray.opt(index) as? JSONObject ?: throw VaultFormatError("items[$index]가 객체가 아니다")
                decodeItem(item)
            },
            extras = root.extras(VAULT_FIELDS),
        )
        VaultRules.validate(vault)
        return vault
    }

    override fun encode(vault: VaultData): ByteArray {
        VaultRules.validate(vault)
        val root = JSONObject()
        vault.extras.forEach { (key, raw) -> root.put(key, JSONTokener(raw).nextValue()) }
        root.put(VERSION, vault.version)
        root.put(UPDATED_AT, formatTime(vault.updatedAt))
        root.put(ITEMS, JSONArray(vault.items.map(::encodeItem)))
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decodeItem(json: JSONObject): VaultItem {
        val deletedAt = json.optionalTime(DELETED_AT)
        val deleted = deletedAt != null
        return VaultItem(
            id = json.requireString(ID),
            serviceName = if (deleted) json.optionalString(SERVICE_NAME) else json.requireString(SERVICE_NAME),
            username = if (deleted) json.optionalString(USERNAME) else json.requireString(USERNAME),
            password = if (deleted) json.optionalString(PASSWORD) else json.requireString(PASSWORD),
            url = json.optionalString(URL),
            notes = json.optionalString(NOTES),
            createdAt = json.requireTime(CREATED_AT),
            updatedAt = json.requireTime(UPDATED_AT),
            deletedAt = deletedAt,
            extras = json.extras(ITEM_FIELDS),
        )
    }

    private fun encodeItem(item: VaultItem): JSONObject {
        val json = JSONObject()
        // 삭제된 항목은 id와 시각만 남긴다(VLT-001 규칙 6)
        if (!item.isDeleted) {
            item.extras.forEach { (key, raw) -> json.put(key, JSONTokener(raw).nextValue()) }
            json.put(SERVICE_NAME, item.serviceName)
            json.put(USERNAME, item.username)
            json.put(PASSWORD, item.password)
            item.url?.let { json.put(URL, it) }
            item.notes?.let { json.put(NOTES, it) }
        }
        json.put(ID, item.id)
        json.put(CREATED_AT, formatTime(item.createdAt))
        json.put(UPDATED_AT, formatTime(item.updatedAt))
        item.deletedAt?.let { json.put(DELETED_AT, formatTime(it)) }
        return json
    }

    private fun JSONObject.requireInt(key: String): Int =
        opt(key) as? Int ?: throw VaultFormatError("$key 필드가 정수가 아니다")

    private fun JSONObject.requireString(key: String): String =
        opt(key) as? String ?: throw VaultFormatError("$key 필드가 문자열이 아니다")

    /** null과 필드 없음은 같다(vault-spec 4장). 다른 타입은 형식 오류. */
    private fun JSONObject.optionalString(key: String): String? = when (val value = opt(key)) {
        null, JSONObject.NULL -> null
        is String -> value
        else -> throw VaultFormatError("$key 필드가 문자열이 아니다")
    }

    private fun JSONObject.requireTime(key: String): Instant = parseTime(requireString(key), key)

    private fun JSONObject.optionalTime(key: String): Instant? = optionalString(key)?.let { parseTime(it, key) }

    private fun JSONObject.extras(known: Set<String>): Map<String, String> =
        keys().asSequence().filterNot { it in known }.associateWith { key -> rawJson(get(key)) }

    private fun rawJson(value: Any): String = when (value) {
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    /** 시간대 표기(Z 또는 오프셋)가 있는 ISO 8601만 받는다. */
    private fun parseTime(text: String, key: String): Instant = try {
        OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (error: DateTimeParseException) {
        throw VaultFormatError("$key 시각에 시간대 표기가 없거나 ISO 8601이 아니다: $text")
    }

    private fun formatTime(time: Instant): String =
        WRITE_FORMAT.format(time.truncatedTo(ChronoUnit.SECONDS))

    private companion object {
        const val VERSION = "version"
        const val UPDATED_AT = "updatedAt"
        const val ITEMS = "items"
        const val ID = "id"
        const val SERVICE_NAME = "serviceName"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val URL = "url"
        const val NOTES = "notes"
        const val CREATED_AT = "createdAt"
        const val DELETED_AT = "deletedAt"
        val VAULT_FIELDS = setOf(VERSION, UPDATED_AT, ITEMS)
        val ITEM_FIELDS = setOf(ID, SERVICE_NAME, USERNAME, PASSWORD, URL, NOTES, CREATED_AT, UPDATED_AT, DELETED_AT)
        val WRITE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    }
}
