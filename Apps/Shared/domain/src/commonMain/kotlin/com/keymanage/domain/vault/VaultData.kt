package com.keymanage.domain.vault

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// 복호화된 평문 볼트 해석과 항목 편집 (vault-spec 4장, VLT-001). 원본: Apps/Web/src/domain/vault/vaultData.ts
// 모든 편집은 원본을 바꾸지 않고 새 볼트를 돌려준다. 모르는 필드는 지우지 않는다.
// 볼트·항목은 JSON 객체를 그대로 감싼다. 키 순서와 모르는 필드가 원본(JSON.stringify)과 같게 남는다

/** 이 앱이 아는 볼트 스키마 버전. 더 높은 버전은 읽기 전용으로 연다 */
const val VAULT_SCHEMA_VERSION: Int = 1

/** 평문 볼트 한 건. [json]이 저장·전송되는 내용 그대로다 */
class VaultData internal constructor(val json: JsonObject) {
    val version: Int get() = checkNotNull(integerOrNull(json["version"]))
    val updatedAt: String get() = json.getValue("updatedAt").stringContent()
    val items: List<VaultItem> by lazy { (json.getValue("items") as JsonArray).map { VaultItem(it as JsonObject) } }

    /** 모르는 최상위 필드를 포함한 원래 값 */
    operator fun get(field: String): JsonElement? = json[field]

    override fun equals(other: Any?): Boolean = other is VaultData && other.json == json
    override fun hashCode(): Int = json.hashCode()
    override fun toString(): String = "VaultData(version=$version, items=${items.size})"

    companion object {
        /** 항목 하나로 된 볼트. 테스트와 단일 항목 편집에 쓴다 */
        fun of(vararg items: VaultItem, updatedAt: String = items.firstOrNull()?.updatedAt ?: "1970-01-01T00:00:00Z"): VaultData =
            VaultData(
                JsonObject(
                    linkedMapOf(
                        "version" to JsonPrimitive(VAULT_SCHEMA_VERSION),
                        "updatedAt" to JsonPrimitive(updatedAt),
                        "items" to JsonArray(items.map { it.json }),
                    ),
                ),
            )
    }
}

/** 볼트 항목 한 건. 삭제된 항목은 id와 시각만 있다 */
class VaultItem internal constructor(val json: JsonObject) {
    val id: String get() = json.getValue("id").stringContent()
    val serviceName: String? get() = text("serviceName")
    val username: String? get() = text("username")
    val password: String? get() = text("password")
    val url: String? get() = text("url")
    val notes: String? get() = text("notes")
    val createdAt: String get() = json.getValue("createdAt").stringContent()
    val updatedAt: String get() = json.getValue("updatedAt").stringContent()
    val deletedAt: String? get() = text("deletedAt")
    val isDeleted: Boolean get() = deletedAt != null

    /** 새 버전 앱이 추가한 필드를 포함한 원래 값 */
    operator fun get(field: String): JsonElement? = json[field]
    operator fun contains(field: String): Boolean = field in json

    private fun text(field: String): String? = (json[field] as? JsonPrimitive)?.takeIf { it.isString }?.content

    override fun equals(other: Any?): Boolean = other is VaultItem && other.json == json
    override fun hashCode(): Int = json.hashCode()
    override fun toString(): String = "VaultItem(id=$id, deleted=$isDeleted)"
}

data class ItemFields(
    val serviceName: String,
    val username: String,
    val password: String,
    val url: String? = null,
    val notes: String? = null,
)

/** 선택 필드(url, notes)를 그대로 두거나, 없애거나, 바꾼다 */
sealed interface OptionalChange {
    data object Keep : OptionalChange
    data object Remove : OptionalChange
    data class Set(val value: String) : OptionalChange
}

/** 항목 수정 내용. null인 필수 필드는 바꾸지 않는다 */
data class ItemChanges(
    val serviceName: String? = null,
    val username: String? = null,
    val password: String? = null,
    val url: OptionalChange = OptionalChange.Keep,
    val notes: OptionalChange = OptionalChange.Keep,
)

/** 복호화는 됐지만 내용이 규격과 다름. 볼트를 열지 않고 서버 볼트도 덮어쓰지 않는다 */
class InvalidVaultDataException(message: String) : IllegalArgumentException(message)

/** 이미 있는 id로 항목을 추가하려 함 */
class DuplicateItemIdException(id: String) : IllegalStateException("이미 있는 항목 id: $id")

/** 수정하려는 항목이 없거나 이미 삭제됨 */
class VaultItemNotFoundException(id: String) : NoSuchElementException("항목 없음: $id")

private val REQUIRED_ITEM_TEXT = listOf("serviceName", "username", "password")
private val OPTIONAL_ITEM_TEXT = listOf("url", "notes")
private val TIMESTAMPS = listOf("createdAt", "updatedAt")

// 다른 플랫폼이 null로 쓴 선택 필드는 없는 것으로 본다
private val NULLABLE_ITEM_FIELDS = setOf("url", "notes", "deletedAt")

// UTC 표기(Z 또는 오프셋)가 없는 시각은 기기 시간대마다 다르게 해석되므로 받지 않는다
private val UTC_TIME = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$""")

private val json = Json

/** @throws InvalidVaultDataException */
fun parseVaultData(text: String): VaultData {
    val raw = try {
        json.parseToJsonElement(text)
    } catch (error: SerializationException) {
        throw InvalidVaultDataException("JSON이 아님")
    }
    if (raw !is JsonObject) throw InvalidVaultDataException("볼트가 객체가 아님")
    val version = integerOrNull(raw["version"])
    if (version == null || version < 1) throw InvalidVaultDataException("version이 1 이상의 정수가 아님")
    if (!isUtcTime(raw["updatedAt"])) throw InvalidVaultDataException("updatedAt 형식이 틀림")
    val rawItems = raw["items"] as? JsonArray ?: throw InvalidVaultDataException("items가 배열이 아님")

    val ids = mutableSetOf<String>()
    val items = rawItems.map { rawItem ->
        val item = validateItem(withoutNullFields(rawItem))
        val id = item.getValue("id").stringContent()
        if (!ids.add(id)) throw InvalidVaultDataException("같은 id가 두 번 나옴: $id")
        item
    }
    return VaultData(JsonObject(raw + ("items" to JsonArray(items))))
}

/** 암호화하기 전의 평문. 원본의 JSON.stringify와 같은 결과다 */
fun encodeVaultData(data: VaultData): String = data.json.toString()

fun isReadOnly(data: VaultData): Boolean = data.version > VAULT_SCHEMA_VERSION

fun emptyVault(now: Instant): VaultData = VaultData(
    JsonObject(
        linkedMapOf(
            "version" to JsonPrimitive(VAULT_SCHEMA_VERSION),
            "updatedAt" to JsonPrimitive(formatTime(now)),
            "items" to JsonArray(emptyList()),
        ),
    ),
)

/** 삭제 표시가 없는 항목 */
fun activeItems(data: VaultData): List<VaultItem> = data.items.filterNot { it.isDeleted }

/** @throws DuplicateItemIdException */
@OptIn(ExperimentalUuidApi::class)
fun addItem(data: VaultData, fields: ItemFields, now: Instant, id: String = Uuid.random().toString()): VaultData {
    if (data.items.any { it.id == id }) throw DuplicateItemIdException(id)
    val time = formatTime(now)
    // 키 순서는 원본 { id, ...fields, createdAt, updatedAt }와 같다
    val item = linkedMapOf<String, JsonElement>("id" to JsonPrimitive(id))
    item["serviceName"] = JsonPrimitive(fields.serviceName)
    item["username"] = JsonPrimitive(fields.username)
    item["password"] = JsonPrimitive(fields.password)
    fields.url?.let { item["url"] = JsonPrimitive(it) }
    fields.notes?.let { item["notes"] = JsonPrimitive(it) }
    item["createdAt"] = JsonPrimitive(time)
    item["updatedAt"] = JsonPrimitive(time)
    return data.withItems(time, data.items.map { it.json } + JsonObject(item))
}

/** @throws VaultItemNotFoundException */
fun updateItem(data: VaultData, id: String, changes: ItemChanges, now: Instant): VaultData =
    replaceItem(data, id, now) { item, time ->
        // 원본 { ...kept, ...set, updatedAt }처럼 있던 키는 자리를 지키고 새 키는 뒤에 붙는다
        val next = LinkedHashMap(item.json)
        changes.serviceName?.let { next["serviceName"] = JsonPrimitive(it) }
        changes.username?.let { next["username"] = JsonPrimitive(it) }
        changes.password?.let { next["password"] = JsonPrimitive(it) }
        applyOptional(next, "url", changes.url)
        applyOptional(next, "notes", changes.notes)
        next["updatedAt"] = JsonPrimitive(time)
        JsonObject(next)
    }

/** 내용을 지우고 삭제 표시만 남긴다. 다른 기기와 병합할 때 삭제가 되살아나지 않게 하기 위해서다 (VLT-001) */
fun deleteItem(data: VaultData, id: String, now: Instant): VaultData = replaceItem(data, id, now) { item, time ->
    JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(item.id),
            "createdAt" to JsonPrimitive(item.createdAt),
            "updatedAt" to JsonPrimitive(time),
            "deletedAt" to JsonPrimitive(time),
        ),
    )
}

/** vault-spec의 시각 형식 YYYY-MM-DDTHH:mm:ssZ. 1초 미만은 버린다 */
fun formatTime(time: Instant): String = Instant.fromEpochSeconds(time.epochSeconds).toString()

/** 읽을 수 없는 시각은 null */
internal fun parseTimeOrNull(time: String?): Instant? = time?.let {
    try {
        Instant.parse(it)
    } catch (error: IllegalArgumentException) {
        null
    }
}

private fun applyOptional(target: MutableMap<String, JsonElement>, field: String, change: OptionalChange) {
    when (change) {
        OptionalChange.Keep -> Unit
        // 지운 선택 필드는 빈 문자열이 아니라 필드 없음으로 남긴다. "값 없음"을 한 가지로만 나타낸다
        OptionalChange.Remove -> target.remove(field)
        is OptionalChange.Set -> target[field] = JsonPrimitive(change.value)
    }
}

private fun replaceItem(data: VaultData, id: String, now: Instant, change: (VaultItem, String) -> JsonObject): VaultData {
    val index = data.items.indexOfFirst { it.id == id && !it.isDeleted }
    if (index == -1) throw VaultItemNotFoundException(id)
    val time = nextChangeTime(data.items[index], now)
    return data.withItems(time, data.items.mapIndexed { i, item -> if (i == index) change(item, time) else item.json })
}

private fun VaultData.withItems(updatedAt: String, items: List<JsonObject>): VaultData =
    VaultData(JsonObject(json + mapOf("updatedAt" to JsonPrimitive(updatedAt), "items" to JsonArray(items))))

// 같은 항목의 변경 시각은 늘 이전보다 커야 병합에서 이긴다. 같은 초 안에 다시 고치거나
// 다른 기기 시계가 앞서 있어도 최소 1초 뒤로 잡는다 (VLT-001)
private fun nextChangeTime(item: VaultItem, now: Instant): String {
    val previous = checkNotNull(parseTimeOrNull(item.updatedAt))
    return formatTime(maxOf(now, previous + 1.seconds))
}

private fun isUtcTime(value: JsonElement?): Boolean =
    value is JsonPrimitive && value.isString && UTC_TIME.matches(value.content) && parseTimeOrNull(value.content) != null

private fun withoutNullFields(item: JsonElement): JsonElement =
    if (item is JsonObject) JsonObject(item.filterNot { (key, value) -> key in NULLABLE_ITEM_FIELDS && value is JsonNull }) else item

/** 규격에 맞는 항목이면 그대로 돌려준다 */
private fun validateItem(element: JsonElement): JsonObject {
    val item = element as? JsonObject
    val id = (item?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (item == null || id == null) throw InvalidVaultDataException("항목 id가 문자열이 아님")
    fun fail(field: String) = InvalidVaultDataException("항목 $id 의 $field 형식이 틀림")
    TIMESTAMPS.forEach { if (!isUtcTime(item[it])) throw fail(it) }
    if ("deletedAt" in item) {
        if (!isUtcTime(item["deletedAt"])) throw fail("deletedAt")
        // 삭제된 항목은 내용이 지워져 있으므로 나머지 필드를 요구하지 않는다
        return item
    }
    REQUIRED_ITEM_TEXT.forEach { if (!isText(item[it])) throw fail(it) }
    OPTIONAL_ITEM_TEXT.forEach { if (it in item && !isText(item[it])) throw fail(it) }
    return item
}

private fun isText(value: JsonElement?): Boolean = value is JsonPrimitive && value.isString

private fun JsonElement.stringContent(): String = (this as JsonPrimitive).content

// 원본 Number.isInteger와 같이 1, 1.0, 1e0을 정수로 받고 1.5와 문자열은 받지 않는다
internal fun integerOrNull(value: JsonElement?): Int? {
    if (value !is JsonPrimitive || value.isString) return null
    val number = value.content.toDoubleOrNull() ?: return null
    if (number != kotlin.math.floor(number) || number > Int.MAX_VALUE || number < Int.MIN_VALUE) return null
    return number.toInt()
}
