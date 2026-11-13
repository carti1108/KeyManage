package com.keymanage.feature.settings

import com.carti.ribs.Dependency
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.SessionPort

interface SettingsDependency : Dependency {
    val settingsViewFactory: SettingsViewFactory
    val session: SessionPort
    val backupFiles: BackupFilesPort
    val environment: AppEnvironment
}

/** 부모에게 올리는 이벤트 */
interface SettingsListener {
    /** 잠그기 버튼 */
    fun settingsDidRequestLock()

    /** 로그아웃 버튼. 동기화 대기 내용 확인(VLT-002)은 부모가 한다 */
    fun settingsDidRequestLogout()

    /** 백업 복원에 성공했다. 부모가 지난 동기화 오류를 걷는다. 이어서 [settingsDidChangeSession]도 불린다 (웹 SessionRoot의 onRestore) */
    fun settingsDidRestoreBackup()

    /** 마스터 비밀번호 변경에 성공했다. 부모가 지난 동기화 오류와 오프라인 표시를 걷는다. 이어서 [settingsDidChangeSession]도 불린다 (웹 SessionRoot의 onChange) */
    fun settingsDidChangeMasterPassword()

    /** 복원·비밀번호 변경·계정 삭제를 시도했다. 성공·실패와 상관없이 불리며, 세션이나 볼트가 바뀌었을 수 있으니 부모가 다시 읽는다 */
    fun settingsDidChangeSession()

    /**
     * 이 화면을 떠나 로그인 화면으로 가야 한다: 세션 만료·다른 곳의 비밀번호 변경, 계정 삭제 완료, 계정은 지웠지만 기기 기록을 지우지 못함.
     * [notice]는 로그인 화면에 보일 안내 문구
     */
    fun settingsDidEndSession(notice: String)
}
