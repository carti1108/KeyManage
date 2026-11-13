@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.carti.ribs.ViewHost
import com.carti.ribs.web.ScreenSlot
import com.carti.ribs.web.WebViewControllable
import com.keymanage.domain.platform.SecureRandomBytes
import com.keymanage.feature.KeyManageApp
import com.keymanage.feature.KeyManageViewFactory
import com.keymanage.feature.SessionPortAdapter
import com.keymanage.feature.auth.AuthPresentable
import com.keymanage.feature.auth.AuthViewContract
import com.keymanage.feature.generator.PasswordGeneratorPresentable
import com.keymanage.feature.generator.PasswordGeneratorViewContract
import com.keymanage.feature.main.MainPresentable
import com.keymanage.feature.main.MainViewContract
import com.keymanage.feature.root.RootViewControllable
import com.keymanage.feature.settings.SettingsPresentable
import com.keymanage.feature.settings.SettingsViewContract
import com.keymanage.feature.unlock.UnlockPresentable
import com.keymanage.feature.unlock.UnlockViewContract
import com.keymanage.feature.vaultlist.VaultListPresentable
import com.keymanage.feature.vaultlist.VaultListViewContract
import com.keymanage.serverhttp.keyManageHttpClient
import io.ktor.client.HttpClient

// 웹 진입점 (domain-migration-plan 11.3, carti ADR-0009). 세션 퍼사드를 만들어 RIB 트리에 붙이고, 루트 화면을 rootSlot에 보인다

/** 화면마다 웹 뷰를 하나씩 만든다. 루트 뷰는 [rootSlot]에 자식 화면을 보인다 */
internal class WebViewFactory(private val rootSlot: ScreenSlot) : KeyManageViewFactory {
    override fun makeRootView(): RootViewControllable = WebRootView(rootSlot)
    override fun makeAuthView(): AuthViewContract = WebAuthView()
    override fun makeUnlockView(): UnlockViewContract = WebUnlockView()
    override fun makeMainView(): MainViewContract = WebMainView()
    override fun makeVaultListView(): VaultListViewContract = WebVaultListView()
    override fun makePasswordGeneratorView(): PasswordGeneratorViewContract = WebGeneratorView()
    override fun makeSettingsView(): SettingsViewContract = WebSettingsView()
}

/** 루트 뷰는 자식 화면을 rootSlot에 직접 보이므로 루트 뷰 자체를 붙일 곳은 없다 */
private object RootHost : ViewHost<RootViewControllable> {
    override fun show(view: RootViewControllable) = Unit
    override fun hide(view: RootViewControllable) = Unit
}

/** 테스트가 MockEngine 클라이언트를 넣는 자리. 앱은 바꾸지 않는다 */
internal var httpClientFactory: () -> HttpClient = { keyManageHttpClient() }

/**
 * React 앱의 진입점. [rootSlot]이 지금 화면(로그인·잠금 해제·볼트)을 담는다.
 * React StrictMode의 이중 마운트와 겹치지 않도록 컴포넌트 밖(main.tsx)에서 [start]·[stop]을 부른다 (ADR-0009).
 *
 * @param apiBaseUrl 빌드할 때 정한 서버 주소. null이나 ""면 같은 출처. localhost가 아니면 HTTPS 출처만 받는다
 * @throws com.keymanage.serverhttp.InvalidBaseUrlException
 */
@JsExport
class WebKeyManageApp(
    apiBaseUrl: String?,
    deviceStore: JsDeviceStore,
    tabs: JsTabs,
    tabMemory: JsTabMemory?,
    clipboard: JsClipboard,
    backupFiles: JsBackupFiles,
    environment: JsEnvironment,
) {
    /**
     * 앱 배선(자동 잠금·대기 동기화·클립보드)이 같은 세션을 쓴다. RIB 화면과 같은 VaultSession을 감싼 것이다.
     * 화면 밖에서 잠그거나 로그아웃해도 화면은 세션 알림으로 따라간다
     */
    val session: KotlinVaultSession = createKotlinSession(apiBaseUrl, deviceStore, tabs, tabMemory, httpClientFactory())

    /** 지금 보이는 화면이 들어온다. screenKey는 "auth" | "unlock" | "main" */
    val rootSlot: ScreenSlot = ScreenSlot()

    private val makeApp: (String?) -> KeyManageApp = { initialNotice ->
        KeyManageApp(
            viewFactory = WebViewFactory(rootSlot),
            session = SessionPortAdapter(session.session),
            clipboard = JsClipboardAdapter(clipboard),
            backupFiles = JsBackupFilesAdapter(backupFiles),
            environment = JsAppEnvironment(environment),
            randomBytes = SecureRandomBytes,
            initialNotice = initialNotice,
        )
    }
    private var app: KeyManageApp? = null

    /**
     * 세션 상태에 맞는 첫 화면을 [rootSlot]에 붙인다. 기기 기록 복원(session.restore) 뒤에 부른다.
     * 이미 시작했으면 아무것도 하지 않는다
     * @param initialNotice 첫 로그인 화면 안내(복원 중 세션 만료·비밀번호 바뀜 등)
     */
    fun start(initialNotice: String? = null) {
        if (app != null) return
        app = makeApp(initialNotice).also { it.start(RootHost) }
    }

    /** RIB 트리를 내리고 rootSlot을 비운다. 세션은 끝내지 않는다 */
    fun stop() {
        val running = app ?: return
        app = null
        running.stop()
        // 루트 뷰를 붙이는 호스트가 없어서, 남은 자식 화면은 여기서 거둔다
        (rootSlot.getSnapshot()?.view as? WebViewControllable)?.let(rootSlot::hide)
    }
}
