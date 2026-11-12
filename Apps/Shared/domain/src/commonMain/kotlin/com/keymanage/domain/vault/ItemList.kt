package com.keymanage.domain.vault

// 화면에 보일 항목 고르기와 입력 검사 (VLT-001). 원본: Apps/Web/src/domain/vault/itemList.ts

/** 삭제되지 않은 항목을 서비스 이름순으로. query는 서비스 이름·아이디·URL에서 대소문자 없이 찾는다 */
fun listItems(data: VaultData, query: String): List<VaultItem> {
    val needle = query.trim().lowercase()
    // 비밀번호·메모는 검색하지 않는다. 화면에 보이지 않는 값으로 항목이 걸리면 혼란스럽다
    fun matches(item: VaultItem) =
        needle.isEmpty() || listOf(item.serviceName, item.username, item.url).any { it?.lowercase()?.contains(needle) == true }
    return activeItems(data)
        .filter(::matches)
        .sortedWith(compareBy<VaultItem, String>(KoreanNaturalOrder) { it.serviceName ?: "" }.thenBy { it.id })
}

enum class ItemFieldsProblem { MISSING_SERVICE_NAME }

fun checkItemFields(fields: ItemFields): ItemFieldsProblem? =
    if (fields.serviceName.isBlank()) ItemFieldsProblem.MISSING_SERVICE_NAME else null

/**
 * 폼에 입력한 값과 기존 항목을 비교해 바뀐 필드만 돌려준다. 바뀐 것이 없으면 null.
 * 병합은 항목 단위라 아무것도 고치지 않고 저장해도 변경 시각이 올라가 그 사이 다른 기기가 고친 항목을 덮어쓴다.
 * 그래서 null이면 저장하지 않는다. 비운 선택 필드는 [OptionalChange.Remove]로 바꿔 빈 문자열을 남기지 않는다 (vault-spec 4장)
 */
fun itemChanges(item: VaultItem, fields: ItemFields): ItemChanges? {
    fun required(before: String?, after: String) = after.takeIf { it != (before ?: "") }
    fun optional(before: String?, after: String?): OptionalChange {
        val value = after ?: ""
        return when {
            value == (before ?: "") -> OptionalChange.Keep
            value.isEmpty() -> OptionalChange.Remove
            else -> OptionalChange.Set(value)
        }
    }
    val changes = ItemChanges(
        serviceName = required(item.serviceName, fields.serviceName),
        username = required(item.username, fields.username),
        password = required(item.password, fields.password),
        url = optional(item.url, fields.url),
        notes = optional(item.notes, fields.notes),
    )
    return changes.takeIf { it != ItemChanges() }
}
