package com.keymanage.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewHost
import com.carti.ribs.compose.ComposeRenderable
import com.carti.ribs.compose.ComposeScreenSlot
import com.carti.ribs.compose.RibSlot
import com.keymanage.android.feature.auth.ui.ComposeAuthView
import com.keymanage.android.feature.generator.ui.ComposePasswordGeneratorView
import com.keymanage.android.feature.main.ui.ComposeMainView
import com.keymanage.android.feature.settings.ui.ComposeSettingsView
import com.keymanage.android.feature.unlock.ui.ComposeUnlockView
import com.keymanage.android.feature.vaultlist.ui.ComposeVaultListView
import com.keymanage.androidsession.AndroidKeyManageApp
import com.keymanage.feature.KeyManageViewFactory
import com.keymanage.feature.root.RootViewControllable
import java.lang.ref.WeakReference

/**
 * 프로세스에 하나 있는 앱 조립. 공유 진입점([AndroidKeyManageApp])에 화면을 만드는 팩토리와 지금 화면만 넘긴다.
 * 도메인·세션·플랫폼 구현은 공유 모듈에 있고 여기에는 뷰 연결만 둔다 (target-architecture 2장)
 */
class KeyManageProcess private constructor(context: Context) {
    private var activity = WeakReference<FragmentActivity>(null)
    private val host = RootHost()

    val app = AndroidKeyManageApp(
        context = context,
        serverUrl = BuildConfig.SERVER_URL,
        viewFactory = ComposeViewFactory,
        activity = { activity.get() },
    )

    suspend fun start() = app.start(host)

    /** 루트 화면. 세션 상태에 따라 로그인·잠금 해제·볼트 중 하나가 들어온다 */
    @Composable
    fun RootContent() {
        (host.shown as? ComposeRenderable)?.Content()
    }

    private class RootHost : ViewHost<RootViewControllable> {
        var shown by mutableStateOf<RootViewControllable?>(null)

        override fun show(view: RootViewControllable) {
            shown = view
        }

        override fun hide(view: RootViewControllable) {
            if (shown === view) shown = null
        }
    }

    companion object {
        private var instance: KeyManageProcess? = null

        /** 지금 화면을 알려 두고 조립을 돌려준다. 생체 인증 창과 파일 선택 창이 이 화면에 뜬다 */
        fun attach(activity: FragmentActivity): KeyManageProcess {
            val process = instance ?: KeyManageProcess(activity.applicationContext).also { instance = it }
            process.activity = WeakReference(activity)
            return process
        }

        fun detach(activity: FragmentActivity) {
            val process = instance ?: return
            if (process.activity.get() === activity) process.activity = WeakReference(null)
        }
    }
}

/** 루트 뷰. 자식 RIB 화면 하나를 보여 준다 */
private class ComposeRootView : RootViewControllable, ComposeRenderable {
    private val slot = ComposeScreenSlot()

    override fun showScreen(view: ViewControllable) = slot.show(view)

    override fun hideScreen(view: ViewControllable) = slot.hide(view)

    @Composable
    override fun Content() = RibSlot(slot)
}

/** 화면마다 Compose 뷰를 하나씩 만든다 */
private object ComposeViewFactory : KeyManageViewFactory {
    override fun makeRootView(): RootViewControllable = ComposeRootView()
    override fun makeAuthView() = ComposeAuthView()
    override fun makeUnlockView() = ComposeUnlockView()
    override fun makeMainView() = ComposeMainView()
    override fun makeVaultListView() = ComposeVaultListView()
    override fun makePasswordGeneratorView() = ComposePasswordGeneratorView()
    override fun makeSettingsView() = ComposeSettingsView()
}
