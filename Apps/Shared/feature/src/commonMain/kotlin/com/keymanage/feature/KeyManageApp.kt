package com.keymanage.feature

import com.carti.ribs.ViewHost
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.root.RootBuilder
import com.keymanage.feature.root.RootDependency
import com.keymanage.feature.root.RootViewControllable
import com.keymanage.feature.root.RootViewFactory
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.unlock.BiometricInvalidatingSession
import com.keymanage.feature.unlock.BiometricUnlock
import com.keymanage.feature.unlock.UnlockKeySession
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.vaultlist.VaultListViewFactory

/** 플랫폼이 구현하는 화면 만들기 전부. 화면마다 하나씩 [XxxViewFactory]를 구현하는 대신 한 객체로 모은다 */
interface KeyManageViewFactory :
    RootViewFactory,
    AuthViewFactory,
    UnlockViewFactory,
    MainViewFactory,
    VaultListViewFactory,
    PasswordGeneratorViewFactory,
    SettingsViewFactory

/**
 * 플랫폼 진입점. 뷰 팩토리와 바깥 기능을 받아 루트 RIB을 만들고, [start]/[stop]으로 트리를 시작하고 끝낸다.
 * 생명주기 호출은 메인 스레드에서 한다. 끝낸 뒤 다시 [start]할 수 있다
 *
 * @param biometricUnlock 생체 해제. 넣지 않으면(웹) 잠금 화면이 기능을 숨긴다. 넣으면 비밀번호 변경·계정 삭제·로그아웃 때 저장한 키를 지운다
 * @param unlockKeySession 생체 해제용 키를 내보내고 그 키로 여는 세션 기능 (ADR-0027). [biometricUnlock]과 함께 넣는다
 * @param initialNotice 시작할 때 이미 정해진 로그인 화면 안내. 세션을 복구하다 만료됐을 때처럼 첫 로그인 화면에 보일 문구를 넘긴다
 */
class KeyManageApp(
    viewFactory: KeyManageViewFactory,
    session: SessionPort,
    clipboard: ClipboardPort,
    backupFiles: BackupFilesPort,
    environment: AppEnvironment,
    randomBytes: RandomBytes,
    initialNotice: String? = null,
    biometricUnlock: BiometricUnlock? = null,
    unlockKeySession: UnlockKeySession? = null,
) {
    private val router = RootBuilder(
        object : RootDependency {
            override val rootViewFactory: RootViewFactory = viewFactory
            override val authViewFactory: AuthViewFactory = viewFactory
            override val unlockViewFactory: UnlockViewFactory = viewFactory
            override val mainViewFactory: MainViewFactory = viewFactory
            override val vaultListViewFactory: VaultListViewFactory = viewFactory
            override val passwordGeneratorViewFactory: PasswordGeneratorViewFactory = viewFactory
            override val settingsViewFactory: SettingsViewFactory = viewFactory
            override val session: SessionPort = biometricUnlock?.let { BiometricInvalidatingSession(session, it) } ?: session
            override val environment: AppEnvironment = environment
            override val clipboard: ClipboardPort = clipboard
            override val backupFiles: BackupFilesPort = backupFiles
            override val randomBytes: RandomBytes = randomBytes
            override val biometricUnlock: BiometricUnlock? = biometricUnlock
            override val unlockKeySession: UnlockKeySession? = unlockKeySession
        },
    ).build(initialNotice)

    /** 루트 화면을 [host]에 보이고 세션 상태에 맞는 첫 화면을 붙인다 */
    fun start(host: ViewHost<RootViewControllable>) {
        router.launch(host)
    }

    /** 트리 전체를 내리고 루트 화면을 거둔다. 시작 전이거나 이미 끝났으면 아무 일도 하지 않는다 */
    fun stop() {
        router.shutdown()
    }
}
