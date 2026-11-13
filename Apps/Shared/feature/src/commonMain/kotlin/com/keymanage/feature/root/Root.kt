package com.keymanage.feature.root

import com.carti.ribs.Builder
import com.carti.ribs.Component
import com.carti.ribs.Dependency
import com.carti.ribs.Interactor
import com.carti.ribs.LaunchRouter
import com.carti.ribs.RibDispatchers
import com.carti.ribs.RibState
import com.carti.ribs.Router
import com.carti.ribs.ViewControllable
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.domain.session.SessionState
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthBuilder
import com.keymanage.feature.auth.AuthDependency
import com.keymanage.feature.auth.AuthListener
import com.keymanage.feature.auth.AuthRouter
import com.keymanage.feature.auth.AuthViewFactory
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import com.keymanage.feature.main.MainBuilder
import com.keymanage.feature.main.MainDependency
import com.keymanage.feature.main.MainListener
import com.keymanage.feature.main.MainRouter
import com.keymanage.feature.main.MainViewFactory
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.sendsToSignIn
import com.keymanage.feature.settings.SettingsViewFactory
import com.keymanage.feature.unlock.UnlockBuilder
import com.keymanage.feature.unlock.UnlockDependency
import com.keymanage.feature.unlock.UnlockListener
import com.keymanage.feature.unlock.UnlockRouter
import com.keymanage.feature.unlock.UnlockViewFactory
import com.keymanage.feature.vaultlist.VaultListViewFactory
import kotlinx.coroutines.launch

// 세션 상태에 따라 로그인·잠금·볼트 화면 중 하나를 붙인다 (SEC-001~003, VLT-002)
// 원본: Apps/Web/src/ui/session/SessionRoot.tsx, iOS Features/Root

/** 루트 뷰 계약. 자식 RIB 화면 하나를 보여 준다. 플랫폼이 구현한다 */
interface RootPresentable : ViewControllable {
    fun showScreen(view: ViewControllable)
    fun hideScreen(view: ViewControllable)
}

fun interface RootViewFactory {
    fun makeRootView(): RootPresentable
}

interface RootDependency : Dependency {
    val rootViewFactory: RootViewFactory
    val session: SessionPort
    val environment: AppEnvironment
    val clipboard: ClipboardPort
    val backupFiles: BackupFilesPort
    val randomBytes: RandomBytes

    // 자식 RIB과 그 자식이 쓸 뷰 팩토리
    val authViewFactory: AuthViewFactory
    val unlockViewFactory: UnlockViewFactory
    val mainViewFactory: MainViewFactory
    val vaultListViewFactory: VaultListViewFactory
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val settingsViewFactory: SettingsViewFactory
}

class RootComponent(dependency: RootDependency) :
    Component<RootDependency>(dependency), AuthDependency, UnlockDependency, MainDependency {
    override val authViewFactory: AuthViewFactory get() = dependency.authViewFactory
    override val unlockViewFactory: UnlockViewFactory get() = dependency.unlockViewFactory
    override val mainViewFactory: MainViewFactory get() = dependency.mainViewFactory
    override val vaultListViewFactory: VaultListViewFactory get() = dependency.vaultListViewFactory
    override val passwordGeneratorViewFactory: PasswordGeneratorViewFactory get() = dependency.passwordGeneratorViewFactory
    override val settingsViewFactory: SettingsViewFactory get() = dependency.settingsViewFactory
    override val session: SessionPort get() = dependency.session
    override val environment: AppEnvironment get() = dependency.environment
    override val clipboard: ClipboardPort get() = dependency.clipboard
    override val backupFiles: BackupFilesPort get() = dependency.backupFiles
    override val randomBytes: RandomBytes get() = dependency.randomBytes
}

class RootBuilder(dependency: RootDependency) : Builder<RootDependency>(dependency) {
    fun build(): RootRouter {
        val component = RootComponent(dependency)
        return RootRouter(
            view = dependency.rootViewFactory.makeRootView(),
            interactor = RootInteractor(dependency.session),
            authBuilder = AuthBuilder(component),
            unlockBuilder = UnlockBuilder(component),
            mainBuilder = MainBuilder(component),
        )
    }
}

enum class RootScreen { AUTH, UNLOCK, MAIN }

/**
 * 시작할 때와 세션이 바뀔 때마다 세션 상태를 읽어 화면을 고른다. 이 탭의 요청 결과는 자식이 알리고,
 * 다른 탭·자동 잠금·세션 만료는 세션 알림으로 안다.
 *
 * 세션 알림은 요청을 처리하는 도중(예: 잠금 해제가 볼트를 연 직후 결과를 돌려주기 전)에 동기로 오므로, 그 자리에서 화면을 바꾸면
 * 진행 중인 자식의 작업이 취소되고 그 결과(오프라인으로 열었는지, 안내 문구)가 사라진다. 그래서 알림은 다음 턴에 처리하고,
 * 로그인·잠금 해제 화면은 결과를 알릴 때까지, 볼트 화면은 세션을 끝내는 작업이 끝날 때까지 내리지 않는다
 */
class RootInteractor(private val session: SessionPort) :
    Interactor<Unit, RootRouter>(Unit), AuthListener, UnlockListener, MainListener {
    private var unsubscribes: List<() -> Unit> = emptyList()

    /** 다음 로그인 화면에 보일 안내(세션 만료 등). 로그인 화면에 넘기면 비운다 */
    private var pendingNotice: String? = null

    override fun didBecomeActive(savedInstanceState: RibState?) {
        unsubscribes = listOf(
            session.onLock { post { refresh(fromChild = false) } },
            session.onUnlock { post { refresh(fromChild = false) } },
            session.onRemoteChange { post { refresh(fromChild = false) } },
            // 세션 만료·다른 곳의 비밀번호 변경은 어느 화면에서든 로그인 화면에 이유를 보인다 (SEC-002, SEC-003)
            session.onSyncChange { error ->
                if (error != null && sendsToSignIn(error)) {
                    val notice = describeError(error)
                    post {
                        pendingNotice = notice
                        refresh(fromChild = false)
                    }
                }
            },
        )
        refresh(fromChild = true)
    }

    override fun willResignActive() {
        unsubscribes.forEach { it() }
        unsubscribes = emptyList()
    }

    /** 세션 콜백은 어느 스레드에서 올지 모르고 요청 도중에 올 수 있으니 메인 디스패처의 다음 턴에 처리한다. 내려갔으면 무시한다 */
    private fun post(work: () -> Unit) {
        if (isAttached()) coroutineScope.launch(RibDispatchers.Main) { work() }
    }

    /**
     * @param fromChild 자식이 요청 결과를 알렸다. 아니면 세션 알림이라, 로그인·잠금 해제 화면이 결과를 알리기 전에는 볼트 화면으로 가지 않는다
     * @param offline 잠금 해제를 서버에 닿지 못해 기기에 저장한 볼트로 열었다
     */
    private fun refresh(fromChild: Boolean, offline: Boolean = false) {
        if (!isAttached()) return
        val current = router.current
        when (val state = session.state()) {
            is SessionState.Unlocked -> {
                if (current == RootScreen.MAIN) return
                if (!fromChild && current != null) return
                router.routeToMain(offline)
            }
            else -> {
                if (current == RootScreen.MAIN && router.isMainFinishingSession) return
                if (state is SessionState.Locked) {
                    router.routeToUnlock()
                } else {
                    val notice = pendingNotice
                    pendingNotice = null
                    router.routeToAuth(notice)
                }
            }
        }
    }

    // --- AuthListener ---

    override fun authDidComplete() {
        pendingNotice = null
        refresh(fromChild = true)
    }

    // --- UnlockListener ---

    override fun didUnlock(offline: Boolean) {
        refresh(fromChild = true, offline = offline)
    }

    override fun didLogout(notice: String?) {
        pendingNotice = notice
        refresh(fromChild = true)
    }

    override fun didRequireSignIn(notice: String) {
        pendingNotice = notice
        refresh(fromChild = true)
    }

    // --- MainListener ---

    override fun didLock() {
        refresh(fromChild = true)
    }
}

class RootRouter(
    view: RootPresentable,
    interactor: RootInteractor,
    private val authBuilder: AuthBuilder,
    private val unlockBuilder: UnlockBuilder,
    private val mainBuilder: MainBuilder,
) : LaunchRouter<RootPresentable, RootInteractor>(view, interactor) {
    private var auth: AuthRouter? = null
    private var authNotice: String? = null
    private var unlock: UnlockRouter? = null
    private var main: MainRouter? = null

    val current: RootScreen?
        get() = when {
            main != null -> RootScreen.MAIN
            unlock != null -> RootScreen.UNLOCK
            auth != null -> RootScreen.AUTH
            else -> null
        }

    /** 볼트 화면이 세션을 끝내는 작업(로그아웃, 계정 삭제)을 하는 중인지. 이 동안은 화면을 내리지 않는다 */
    val isMainFinishingSession: Boolean get() = main?.interactor?.isFinishingSession ?: false

    /**
     * @param notice 로그인 화면에 보일 안내. 이미 로그인 화면이 떠 있으면 안내가 있고 이미 보인 것과 다를 때만 새로 만든다
     * (안내가 화면을 만든 뒤에 도착하는 경우: 로그아웃 결과, 다른 곳의 비밀번호 변경)
     */
    fun routeToAuth(notice: String?) {
        if (auth != null) {
            if (notice == null || notice == authNotice) return
            removeAuth()
        }
        removeUnlock()
        removeMain()
        val child = authBuilder.build(interactor, notice)
        attachChild(child)
        view.showScreen(child.view)
        auth = child
        authNotice = notice
    }

    fun routeToUnlock() {
        if (unlock != null) return
        removeAuth()
        removeMain()
        val child = unlockBuilder.build(interactor)
        attachChild(child)
        view.showScreen(child.view)
        unlock = child
    }

    fun routeToMain(offline: Boolean) {
        if (main != null) return
        removeAuth()
        removeUnlock()
        val child = mainBuilder.build(interactor, offline)
        attachChild(child)
        view.showScreen(child.view)
        main = child
    }

    private fun removeAuth() {
        val child = auth ?: return
        remove(child, child.view)
        auth = null
        authNotice = null
    }

    private fun removeUnlock() {
        val child = unlock ?: return
        remove(child, child.view)
        unlock = null
    }

    private fun removeMain() {
        val child = main ?: return
        remove(child, child.view)
        main = null
    }

    private fun remove(child: Router<*>, screen: ViewControllable) {
        detachChild(child)
        view.hideScreen(screen)
    }
}
