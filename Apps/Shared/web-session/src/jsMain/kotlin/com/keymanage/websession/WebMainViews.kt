@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.carti.ribs.ViewControllable
import com.carti.ribs.web.ScreenSlot
import com.carti.ribs.web.ViewStore
import com.carti.ribs.web.WebViewControllable
import com.keymanage.feature.main.MainPresentable
import com.keymanage.feature.main.MainPresentableListener
import com.keymanage.feature.main.MainTab
import com.keymanage.feature.main.MainViewAction
import com.keymanage.feature.main.MainViewState
import com.keymanage.feature.root.RootPresentable

// 루트(Root)와 볼트 껍데기(Main)의 웹 뷰. 둘 다 자식 화면을 ScreenSlot으로 보인다

/** 로그인·잠금·볼트 화면 중 지금 것을 [slot]에 보인다. 앱은 [WebKeyManageApp.rootSlot]을 slot으로 넘기므로 React는 rootSlot만 그리면 된다 */
@JsExport
class WebRootView internal constructor(val slot: ScreenSlot) : RootPresentable, WebViewControllable {
    override val screenKey: String = "root"

    @JsExport.Ignore
    override fun showScreen(view: ViewControllable) = slot.show(view.asWeb())

    @JsExport.Ignore
    override fun hideScreen(view: ViewControllable) = slot.hide(view.asWeb())
}

/** 볼트 껍데기가 그리는 전부. [selectedTab]은 'vault' | 'generator' | 'settings' */
@JsExport
data class WebMainState(
    val email: String,
    val selectedTab: String,
    /** 새 버전 앱이 저장한 볼트라 읽기만 할 수 있다 */
    val readOnly: Boolean,
    /** 서버와 맞추지 못한 상태 (VLT-002) */
    val offline: Boolean,
    /** 대기 내용 재전송이 분명한 거절을 받았을 때의 오류. 다음 동기화 성공에 걷힌다 */
    val syncError: WebMessage?,
    val showsDeviceStorageWarning: Boolean,
    /** 세션 없이 열었다. 마스터 비밀번호를 다시 입력받아야 동기화한다 */
    val needsReconnect: Boolean,
    val reconnectPassword: String,
    val isReconnecting: Boolean,
    val reconnectError: WebMessage?,
    val isConfirmingReconnectRollback: Boolean,
    val isConfirmingLogout: Boolean,
    val isLoggingOut: Boolean,
    /** [offline]이 true일 때의 안내 문구. 아니면 null */
    val offlineNotice: String?,
    val readOnlyNotice: String?,
    val deviceStorageWarning: String?,
)

private fun MainTab.wire(): String = when (this) {
    MainTab.VAULT -> "vault"
    MainTab.GENERATOR -> "generator"
    MainTab.SETTINGS -> "settings"
}

private fun mainTabOf(wire: String): MainTab = when (wire) {
    "vault" -> MainTab.VAULT
    "generator" -> MainTab.GENERATOR
    "settings" -> MainTab.SETTINGS
    else -> throw IllegalArgumentException("알 수 없는 탭: $wire")
}

private fun MainViewState.toWeb() = WebMainState(
    email = email,
    selectedTab = selectedTab.wire(),
    readOnly = readOnly,
    offline = offline,
    syncError = syncError?.toWeb(),
    showsDeviceStorageWarning = showsDeviceStorageWarning,
    needsReconnect = needsReconnect,
    reconnectPassword = reconnectPassword,
    isReconnecting = isReconnecting,
    reconnectError = reconnectError?.toWeb(),
    isConfirmingReconnectRollback = isConfirmingReconnectRollback,
    isConfirmingLogout = isConfirmingLogout,
    isLoggingOut = isLoggingOut,
    offlineNotice = offlineNotice,
    readOnlyNotice = readOnlyNotice,
    deviceStorageWarning = deviceStorageWarning,
)

@JsExport
class WebMainView : MainPresentable, WebViewControllable {
    override val screenKey: String = "main"
    val store: ViewStore<WebMainState> = ViewStore(MainViewState().toWeb())

    /** 고른 탭의 화면(볼트 목록·생성기·설정)이 들어온다 */
    val slot: ScreenSlot = ScreenSlot()

    @JsExport.Ignore
    override var listener: MainPresentableListener? = null

    /** @param tab 'vault' | 'generator' | 'settings' */
    fun selectTab(tab: String) = send(MainViewAction.SelectTab(mainTabOf(tab)))
    fun requestLock() = send(MainViewAction.RequestLock)
    fun requestLogout() = send(MainViewAction.RequestLogout)
    fun confirmLogout() = send(MainViewAction.ConfirmLogout)
    fun cancelLogout() = send(MainViewAction.CancelLogout)
    fun updateReconnectPassword(value: String) = send(MainViewAction.UpdateReconnectPassword(value))
    fun reconnect() = send(MainViewAction.Reconnect)
    fun acceptReconnectRollback() = send(MainViewAction.AcceptReconnectRollback)
    fun cancelReconnectRollback() = send(MainViewAction.CancelReconnectRollback)

    private fun send(action: MainViewAction) {
        listener?.handle(action)
    }

    @JsExport.Ignore
    override fun render(state: MainViewState) = store.set(state.toWeb())

    @JsExport.Ignore
    override fun showScreen(view: ViewControllable) = slot.show(view.asWeb())

    @JsExport.Ignore
    override fun hideScreen(view: ViewControllable) = slot.hide(view.asWeb())
}
