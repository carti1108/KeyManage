package com.keymanage.android.domain.vault

import java.time.Instant

/**
 * 복호화한 평문 볼트(vault-spec 4장).
 * [extras]는 이 앱이 모르는 최상위 필드다. 값은 원래 JSON 조각 그대로 두고 저장할 때 다시 쓴다(VLT-001 규칙 7).
 */
data class VaultData(
    val version: Int,
    val updatedAt: Instant,
    val items: List<VaultItem>,
    val extras: Map<String, String> = emptyMap(),
) {
    /** 앱이 아는 것보다 높은 버전이면 읽기 전용으로 열고 저장하지 않는다(VLT-001 규칙 8). */
    val isReadOnly: Boolean
        get() = version > SUPPORTED_VERSION

    /** 화면에 보일 항목. 삭제 표시가 있는 항목은 뺀다. */
    val visibleItems: List<VaultItem>
        get() = items.filterNot { it.isDeleted }

    companion object {
        const val SUPPORTED_VERSION = 1
    }
}

/**
 * 볼트 항목 하나. 삭제된 항목(삭제 표시)은 내용 필드가 null이고 [deletedAt]이 있다.
 * [extras]는 이 앱이 모르는 항목 필드의 원래 JSON 조각이다.
 */
data class VaultItem(
    val id: String,
    val serviceName: String?,
    val username: String?,
    val password: String?,
    val url: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val extras: Map<String, String> = emptyMap(),
) {
    val isDeleted: Boolean
        get() = deletedAt != null

    /** 병합 기준 시각: 삭제 표시가 있으면 updatedAt과 deletedAt 중 늦은 값(vault-spec 4장). */
    val lastChange: Instant
        get() = deletedAt?.let { maxOf(it, updatedAt) } ?: updatedAt
}

/** 복호화는 됐지만 볼트가 규격에 맞지 않음. 볼트를 열지 않고 서버 볼트를 덮어쓰지 않는다. */
class VaultFormatError(message: String) : Exception(message)
