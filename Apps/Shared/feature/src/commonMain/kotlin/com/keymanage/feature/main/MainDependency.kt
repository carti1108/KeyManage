package com.keymanage.feature.main

import com.carti.ribs.Dependency
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.vaultlist.VaultListViewFactory

interface MainDependency : Dependency {
    val mainViewFactory: MainViewFactory
    val session: SessionPort
    val environment: AppEnvironment
    val clipboard: ClipboardPort
    val backupFiles: BackupFilesPort
    val randomBytes: RandomBytes

    // 자식 RIB이 쓸 뷰 팩토리
    val vaultListViewFactory: VaultListViewFactory
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val settingsViewFactory: SettingsViewFactory
}

/** 자식 → 부모 */
interface MainListener {
    /** 잠그기를 눌렀다(세션은 이미 잠갔다). 또는 세션이 끝나 이 화면을 내려야 한다. 부모가 세션 상태를 다시 읽는다 */
    fun didLock()

    /**
     * 로그아웃했다. 이 기기의 볼트 기록을 지우지 못했으면 로그인 화면에 보일 [notice]를 담는다 (ADR-0019).
     * 서버 토큰 폐기는 기다리지도 알리지도 않는다. 세션이 자기 범위에서 끝까지 한다 (SEC-002)
     */
    fun didLogout(notice: String?)

    /** 세션이 끝났다: 재연결에서 만료·다른 곳의 비밀번호 변경, 계정 삭제 완료 등. 로그인 화면에 [notice]를 보인다 (SEC-002~005) */
    fun didRequireSignIn(notice: String)
}
