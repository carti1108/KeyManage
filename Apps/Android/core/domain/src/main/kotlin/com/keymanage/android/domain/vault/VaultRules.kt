package com.keymanage.android.domain.vault

import java.time.Instant
import java.time.temporal.ChronoUnit

/** 볼트 편집·검증 규칙(vault-spec 4장, VLT-001). */
object VaultRules {
    private const val MIN_VERSION = 1

    /**
     * 항목을 수정·삭제할 때 쓸 시각. 현재 시각과 이전 시각 + 1초 중 큰 값이고 초 단위로 자른다.
     * 같은 초에 다시 고치거나 다른 기기 시계가 앞서 있어도 방금 한 변경이 병합에서 지지 않게 한다.
     */
    fun nextChangeTime(previous: Instant, now: Instant): Instant =
        maxOf(now.truncatedTo(ChronoUnit.SECONDS), previous.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1))

    /** 삭제 표시로 바꾼다. id·createdAt·updatedAt·deletedAt만 남기고 나머지(모르는 필드 포함)는 지운다. */
    fun tombstone(item: VaultItem, now: Instant): VaultItem {
        val time = nextChangeTime(item.lastChange, now)
        return VaultItem(
            id = item.id,
            serviceName = null,
            username = null,
            password = null,
            createdAt = item.createdAt,
            updatedAt = time,
            deletedAt = time,
        )
    }

    /** 저장 전·복호화 직후 확인. 규격에 맞지 않으면 [VaultFormatError]. */
    fun validate(vault: VaultData) {
        if (vault.version < MIN_VERSION) throw VaultFormatError("볼트 버전이 1보다 작다: ${vault.version}")
        val seen = HashSet<String>()
        for (item in vault.items) {
            if (item.id.isBlank()) throw VaultFormatError("항목 id가 비어 있다")
            if (!seen.add(item.id)) throw VaultFormatError("항목 id가 겹친다: ${item.id}")
            if (item.isDeleted) continue
            if (item.serviceName.isNullOrBlank()) throw VaultFormatError("서비스 이름이 비어 있다: ${item.id}")
            if (item.username == null || item.password == null) throw VaultFormatError("아이디·비밀번호 필드가 없다: ${item.id}")
        }
    }
}
