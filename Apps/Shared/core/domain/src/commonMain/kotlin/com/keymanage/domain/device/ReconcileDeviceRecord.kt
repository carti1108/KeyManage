package com.keymanage.domain.device

import com.keymanage.domain.auth.UnlockKdf

// 기기 기록을 쓰기 직전, 다른 탭이 그사이 쓴 기록과 어떻게 맞출지 정한다 (ADR-WEB-001)
// 원본: Apps/Web/src/domain/device/reconcileDeviceRecord.ts. 탭 간 잠금 안에서 기록을 다시 읽은 뒤 부르는 순수 판단이다

sealed interface Reconciliation {
    /** 그대로 써도 된다. 기록이 이 탭이 마지막으로 본 것과 같거나, 처음 쓴다 */
    data object Write : Reconciliation

    /** 다른 탭이 썼다. 잠금 해제 상태라 병합한 뒤 쓴다 */
    data class Merge(val record: DeviceRecord) : Reconciliation

    /** 잠긴 탭인데 다른 탭이 썼다. 키가 없어 병합할 수 없으니 쓰지 않는다 */
    data object Skip : Reconciliation

    /** 이 탭이 본 기록이 사라졌거나 다른 계정의 기록이다. 다른 탭에서 로그아웃·계정 전환이 있었다 */
    data object LoggedOutElsewhere : Reconciliation

    /** 같은 이메일인데 KDF 값이 다르다. 다른 탭에서 바뀐 비밀번호로 로그인했다 (SEC-003) */
    data object PasswordChanged : Reconciliation
}

data class SyncFields(val revision: Int, val lastSeenRevision: Int, val pendingSync: Boolean)

/**
 * @param current 잠금 안에서 방금 읽은 기록
 * @param lastSeen 이 탭이 마지막으로 읽거나 쓴 기록
 */
fun reconcileDeviceRecord(current: DeviceRecord?, lastSeen: DeviceRecord?, email: String, kdf: UnlockKdf, unlocked: Boolean): Reconciliation {
    if (current == null) return if (lastSeen == null) Reconciliation.Write else Reconciliation.LoggedOutElsewhere
    // 이 탭이 본 그대로면 다른 탭이 쓰지 않았다. 이 탭이 새 비밀번호로 로그인해 옛 KDF 기록을 바꾸는 경우도 여기서 쓴다
    if (lastSeen != null && current == lastSeen) return Reconciliation.Write
    if (current.email != email) return Reconciliation.LoggedOutElsewhere
    if (current.kdf != kdf) return Reconciliation.PasswordChanged
    return if (unlocked) Reconciliation.Merge(current) else Reconciliation.Skip
}

/** 병합 결과의 동기화 필드. pendingSync는 OR, revision·lastSeenRevision은 큰 값(되돌림 기준을 낮추지 않음) */
fun mergeSyncFields(mine: SyncFields, theirs: DeviceRecord): SyncFields = SyncFields(
    revision = maxOf(mine.revision, theirs.revision),
    lastSeenRevision = maxOf(mine.lastSeenRevision, theirs.lastSeenRevision),
    pendingSync = mine.pendingSync || theirs.pendingSync,
)
