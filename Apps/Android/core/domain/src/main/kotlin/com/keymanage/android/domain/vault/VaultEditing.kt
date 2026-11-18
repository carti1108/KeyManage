package com.keymanage.android.domain.vault

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 항목 입력값. 선택 필드(url·notes)는 빈 문자열이면 필드 없음으로 저장한다(vault-spec 4장). */
data class ItemFields(
    val serviceName: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = "",
) {
    override fun toString() = "ItemFields(serviceName=$serviceName)"

    companion object {
        fun of(item: VaultItem) = ItemFields(
            serviceName = item.serviceName.orEmpty(),
            username = item.username.orEmpty(),
            password = item.password.orEmpty(),
            url = item.url.orEmpty(),
            notes = item.notes.orEmpty(),
        )
    }
}

enum class ItemFieldsProblem { MISSING_SERVICE_NAME }

/** 수정·삭제할 항목이 없거나 이미 지워졌다. */
class VaultItemNotFound(id: String) : Exception("항목 없음: $id")

/** 항목 추가·수정·삭제(VLT-001). 웹 `addItem`·`updateItem`·`deleteItem`·`itemChanges`와 같은 규칙이다. */
object VaultEditing {
    fun check(fields: ItemFields): ItemFieldsProblem? =
        if (fields.serviceName.isBlank()) ItemFieldsProblem.MISSING_SERVICE_NAME else null

    fun add(vault: VaultData, fields: ItemFields, now: Instant, id: String = UUID.randomUUID().toString()): VaultData {
        require(vault.items.none { it.id == id }) { "같은 id가 이미 있음: $id" }
        val time = now.truncatedTo(ChronoUnit.SECONDS)
        val item = VaultItem(
            id = id,
            serviceName = fields.serviceName,
            username = fields.username,
            password = fields.password,
            url = fields.url.ifEmpty { null },
            notes = fields.notes.ifEmpty { null },
            createdAt = time,
            updatedAt = time,
        )
        return vault.copy(updatedAt = maxOf(vault.updatedAt, time), items = vault.items + item)
    }

    /**
     * 바뀐 필드만 고친다. 바뀐 것이 없으면 null을 돌려주고 저장하지 않는다.
     * 아무것도 고치지 않고 저장해도 변경 시각이 올라가 그 사이 다른 기기가 고친 항목을 덮어쓰기 때문이다.
     */
    fun update(vault: VaultData, id: String, fields: ItemFields, now: Instant): VaultData? {
        val current = activeItem(vault, id)
        if (ItemFields.of(current) == fields) return null
        val time = VaultRules.nextChangeTime(current.lastChange, now)
        val updated = current.copy(
            serviceName = fields.serviceName,
            username = fields.username,
            password = fields.password,
            url = fields.url.ifEmpty { null },
            notes = fields.notes.ifEmpty { null },
            updatedAt = time,
        )
        return replace(vault, updated)
    }

    /** 내용을 지우고 삭제 표시만 남긴다. 다른 기기와 병합할 때 삭제가 되살아나지 않게 하기 위해서다. */
    fun delete(vault: VaultData, id: String, now: Instant): VaultData = replace(vault, VaultRules.tombstone(activeItem(vault, id), now))

    private fun activeItem(vault: VaultData, id: String): VaultItem =
        vault.items.firstOrNull { it.id == id && !it.isDeleted } ?: throw VaultItemNotFound(id)

    private fun replace(vault: VaultData, item: VaultItem): VaultData =
        vault.copy(updatedAt = maxOf(vault.updatedAt, item.updatedAt), items = vault.items.map { if (it.id == item.id) item else it })
}
