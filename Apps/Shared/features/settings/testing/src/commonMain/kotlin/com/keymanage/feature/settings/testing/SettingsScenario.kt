package com.keymanage.feature.settings.testing

import com.keymanage.domain.backup.BackupDecryptException
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.WrongMasterPasswordException
import kotlinx.coroutines.CompletableDeferred

/**
 * 설정 화면을 한 가지 상황으로 띄우는 시나리오. Android·iOS·웹의 Example 앱이 같은 목록을 보여 주고,
 * 고른 시나리오의 [session]·[backupFiles]·[environment]로 Settings RIB을 만든다 (modularization-plan 3장 3번)
 */
enum class SettingsScenario(val title: String) {
    NORMAL("정상 (내보내기·복원·비밀번호 변경·계정 삭제 성공)"),
    OFFLINE("오프라인 (비밀번호 변경·계정 삭제 안내)"),
    READ_ONLY("읽기 전용 (복원·비밀번호 변경 막힘)"),
    WRONG_PASSWORD("틀린 비밀번호 (복원·비밀번호 변경·계정 삭제 실패)"),
    NETWORK_ERROR("서버에 연결할 수 없음 (비밀번호 변경·계정 삭제 실패)"),
    SESSION_EXPIRED("세션 만료 (비밀번호 변경 → 로그인 화면으로)"),
    SLOW("지연 (요청이 끝나지 않음)"),
    NO_FILE_PICKED("복원: 파일 선택 취소"),
    ;

    /** 이 상황을 만드는 가짜 세션 */
    fun session(): FakeSettingsSession = when (this) {
        NORMAL, NO_FILE_PICKED -> FakeSettingsSession().also { it.restoreCount = 3 }
        OFFLINE -> FakeSettingsSession(unlockedSettingsState(signedIn = false))
        READ_ONLY -> FakeSettingsSession(unlockedSettingsState(readOnly = true))
        WRONG_PASSWORD -> FakeSettingsSession().also {
            it.restoreError = BackupDecryptException()
            it.changeError = WrongMasterPasswordException()
            it.deleteError = WrongMasterPasswordException()
        }
        NETWORK_ERROR -> FakeSettingsSession().also {
            it.changeError = NetworkError()
            it.deleteError = NetworkError()
        }
        SESSION_EXPIRED -> FakeSettingsSession().also { it.changeError = SessionExpiredException() }
        SLOW -> FakeSettingsSession().also {
            it.exportGate = CompletableDeferred()
            it.restoreGate = CompletableDeferred()
            it.changeGate = CompletableDeferred()
            it.deleteGate = CompletableDeferred()
        }
    }

    /** 이 상황을 만드는 백업 파일 포트. 복원에서는 형식이 맞는 파일을 고른 것으로 한다 */
    fun backupFiles(): FakeSettingsBackupFiles = FakeSettingsBackupFiles().also { files ->
        if (this != NO_FILE_PICKED) files.picked = sampleBackupSource()
    }

    /** 이 상황을 만드는 앱 환경 */
    fun environment(): FakeSettingsEnvironment = FakeSettingsEnvironment().also { environment ->
        environment.serverAddress = "https://keymanage.example"
        environment.offline = this == OFFLINE
    }
}
