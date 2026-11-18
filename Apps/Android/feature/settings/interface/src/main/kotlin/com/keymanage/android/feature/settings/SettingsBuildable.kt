package com.keymanage.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.domain.vault.VaultData
import com.uber.rib.core.Router
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Instant
import java.time.LocalDate

/** 설정 탭 RIB(목업 7단계). */
interface SettingsBuildable {
    fun build(slot: MutableState<@Composable () -> Unit>, email: String, backup: BackupPort, listener: SettingsListener): Router<*>
}

/** 서버에 올리지 못한 변경 사항이 있는지(VLT-002 규칙 11). */
fun interface PendingChanges {
    fun exist(): Boolean
}

interface SettingsDependency {
    val autoLockStore: AutoLockSettingStore
    val pendingChanges: PendingChanges
    val documents: DocumentIo
    val workDispatcher: CoroutineDispatcher
    val clock: () -> Instant
    val today: () -> LocalDate
}

interface SettingsListener {
    /** 자동 잠금 시간을 바꿨다. 그때부터 새 시간으로 잰다(SEC-003 값 표). */
    fun onAutoLockChanged(option: AutoLockOption)

    /** 로그아웃을 확정했다. */
    fun onLogoutRequested()
}

/** 복원 결과. [vault]는 되돌린 볼트이며 되돌린 항목이 없으면 null이다. */
class RestoreOutcome(val restored: Int, val vault: VaultData?)

/** 잠금이 풀린 볼트의 백업 내보내기·복원(VLT-004). 키를 쥔 부모가 맡는다. 메인 스레드 밖에서 부른다. */
interface BackupPort {
    fun export(now: Instant): ByteArray

    /** 실패는 `BackupError`·`SyncError`·`VaultFormatError`로 던진다. */
    fun restore(bytes: ByteArray, password: String, now: Instant): RestoreOutcome
}

/** 사용자가 고른 문서(저장소 접근 프레임워크 URI) 읽기·쓰기. 실패는 IOException. */
interface DocumentIo {
    /** [maxBytes]를 넘는 파일은 그 바이트까지만 읽는다. 크기 검사는 받은 쪽이 한다. */
    fun read(uri: String, maxBytes: Int): ByteArray
    fun write(uri: String, bytes: ByteArray)
}
