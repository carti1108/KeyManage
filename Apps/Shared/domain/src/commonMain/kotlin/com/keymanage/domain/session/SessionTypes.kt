package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.TabMemory
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.domain.platform.SecureRandomBytes
import com.keymanage.domain.tabs.Tabs
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultSync
import kotlinx.coroutines.CoroutineScope

// 세션이 밖으로 내보내는 오류·상태 타입과 세션 안에서 나누는 값의 모양 (SEC-002, SEC-003)
// 원본: Apps/Web/src/domain/session/sessionTypes.ts

/** 마스터 비밀번호가 틀려 저장해 둔 볼트를 열 수 없음 */
class WrongMasterPasswordException : Exception("마스터 비밀번호가 올바르지 않음")

/** 지금 상태에서는 할 수 없는 동작이거나, 기다리는 사이 잠금·로그아웃으로 취소된 작업 */
class SessionStateException(message: String) : Exception(message)

/** 세션 토큰이 만료·폐기됨. 로그아웃 상태로 바뀌었으니 다시 로그인해야 한다 (SEC-002) */
class SessionExpiredException : Exception("세션이 만료됨")

/** 가입은 됐지만 빈 볼트를 저장하지 못함. 다시 가입하지 말고 로그인하면 그때 볼트를 만든다 (SEC-003) */
class VaultSetupException(cause: Throwable) : Exception("가입 뒤 볼트 준비 실패", cause)

sealed interface SessionState {
    data object SignedOut : SessionState

    data class Locked(
        val email: String,
        /** 서버에 올리지 못한 변경 사항이 암호화된 채 남아 있음 (VLT-002) */
        val pendingSync: Boolean,
    ) : SessionState

    data class Unlocked(
        val email: String,
        val vault: SyncedVault,
        /** 서버에 올리지 못한 변경 사항이 있음 (VLT-002) */
        val pendingSync: Boolean,
        /** 세션 토큰이 있음. 없으면(오프라인으로 다시 열었을 때) 비밀번호를 다시 입력받아야 동기화한다 (SEC-003) */
        val signedIn: Boolean,
        /** 마지막 기기 저장이 성공함. 실패하면 새로고침 때 올리지 못한 변경 사항이 사라질 수 있다 (VLT-003) */
        val storedOnDevice: Boolean,
    ) : SessionState
}

class VaultSessionDeps(
    val auth: AuthService,
    /** revisionMemory는 세션이 넘기는 이번 로그인 전용 창이다. 로그인이 바뀐 뒤 늦게 온 응답은 기준을 바꾸지 못한다 */
    val vaultSyncFor: (accessToken: String, encryptionKey: EncryptionKey, revisionMemory: RevisionMemory) -> VaultSync,
    /** 되돌림 감지 기준. 로그아웃·계정 전환 때 지운다 */
    val revisionMemory: RevisionMemory,
    /** 한 계정의 기록을 두는 기기 저장소 (ADR-0019) */
    val deviceStore: DeviceRecordStore,
    /** 같은 브라우저의 다른 탭과 기기 기록 쓰기를 나누고 변경을 알린다 (ADR-WEB-001) */
    val tabs: Tabs,
    /**
     * 기다리지 않는 작업(쓰지 않게 된 토큰 폐기, 다른 탭 알림에 따른 기기 기록 다시 읽기)을 띄울 곳.
     * 원본은 Promise를 기다리지 않고 버렸다. 세션 상태는 한 스레드에서만 바꾸므로 메인(UI) 디스패처의 범위를 넘긴다
     */
    val scope: CoroutineScope,
    /** 이 탭이 마지막으로 연 계정의 KDF 값. 새로 열렸을 때 다른 탭의 비밀번호 변경을 알아챈다. 없으면 기억하지 않는다 (SEC-003 규칙 11) */
    val tabMemory: TabMemory? = null,
    /** 비밀번호 변경의 새 salt. 원본은 crypto.getRandomValues를 직접 불렀다 */
    val random: RandomBytes = SecureRandomBytes,
)

// 잠겨도 남는 값. 비밀이 아니거나(KDF 값) 암호화돼 있다(볼트). 원본처럼 같은 값인지는 객체 동일성(===)으로 본다
internal data class Account(
    val email: String,
    /** 기기 기록으로 다시 열었고 아직 로그인하지 못했으면 null (ADR-0019) */
    val accessToken: String?,
    val kdf: UnlockKdf,
    /** 마지막 볼트. 동기화 대기 중이면 대기 내용을 담은 볼트다 */
    val payload: EncryptedVaultPayload,
    /** payload가 바탕으로 삼은 서버 revision */
    val revision: Int,
    val pendingSync: Boolean,
)

// 잠금 해제 동안만 있는 값
internal class Unlocked(
    val sync: VaultSync,
    val vault: SyncedVault,
    val pendingSync: Boolean,
)

// 세션 만료로 로그아웃될 때 남긴 동기화 대기 내용. 같은 계정으로 다시 로그인하면 올린다 (VLT-002)
internal class OrphanedPending(
    val email: String,
    val payload: EncryptedVaultPayload,
    val revision: Int,
)

/** 대기 내용을 다시 보낸 결과. null이면 모두 올렸다 */
typealias SyncChangeListener = (error: Throwable?) -> Unit

/** ALL_TABS: 같은 계정의 모든 탭을 잠근다. THIS_TAB: 이 탭만 잠근다 (SEC-003, ADR-WEB-001) */
enum class LockScope { ALL_TABS, THIS_TAB }
