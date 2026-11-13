package com.keymanage.feature.root

import com.carti.ribs.Interactor
import com.carti.ribs.RibDispatchers
import com.keymanage.domain.session.SessionState
import com.keymanage.feature.SessionPort
import com.keymanage.feature.auth.AuthListener
import com.keymanage.feature.main.MainListener
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.sendsToSignIn
import com.keymanage.feature.unlock.UnlockListener
import kotlinx.coroutines.launch

// 세션 상태에 따라 로그인·잠금·볼트 화면 중 하나를 붙인다 (SEC-001~003, VLT-002)
// 원본: Apps/Web/src/ui/session/SessionRoot.tsx, iOS Features/Root

enum class RootScreen { AUTH, UNLOCK, MAIN }

/**
 * 시작할 때와 세션이 바뀔 때마다 세션 상태를 읽어 화면을 고른다. 이 탭의 요청 결과는 자식이 알리고,
 * 다른 탭·자동 잠금·세션 만료는 세션 알림으로 안다.
 *
 * 세션 알림은 요청을 처리하는 도중(예: 잠금 해제가 볼트를 연 직후 결과를 돌려주기 전)에 동기로 오므로, 그 자리에서 화면을 바꾸면
 * 진행 중인 자식의 작업이 취소되고 그 결과(오프라인으로 열었는지, 안내 문구)가 사라진다. 그래서 알림은 다음 턴에 처리하고,
 * 로그인·잠금 해제 화면은 결과를 알릴 때까지, 볼트 화면은 세션을 끝내는 작업이 끝날 때까지 내리지 않는다
 */
class RootInteractor(
    private val session: SessionPort,
    /** 앱을 시작할 때 이미 정해진 로그인 화면 안내(세션 복구 실패 등). 첫 로그인 화면에 보이고 비운다 (웹 SessionRoot의 restoreError) */
    initialNotice: String? = null,
) :
    Interactor(), AuthListener, UnlockListener, MainListener {
    /** Router가 만들어질 때 자신을 넣는다 */
    var router: RootRouter? = null
    private var unsubscribes: List<() -> Unit> = emptyList()

    /** 다음 로그인 화면에 보일 안내(세션 만료 등). 로그인 화면에 넘기면 비운다 */
    private var pendingNotice: String? = initialNotice

    override fun didBecomeActive() {
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
        // 붙어 있던 화면을 내리고 필드를 비운다. 비우지 않으면 다시 시작할 때 이미 붙어 있다고 보고 새로 붙이지 않으며,
        // 루트 뷰를 다시 쓰는 플랫폼에서는 이전 화면이 남는다
        router?.clearScreens()
    }

    /** 세션 콜백은 어느 스레드에서 올지 모르고 요청 도중에 올 수 있으니 메인 디스패처의 다음 턴에 처리한다. 내려갔으면 무시한다 */
    private fun post(work: () -> Unit) {
        if (isActive) coroutineScope.launch(RibDispatchers.Main) { work() }
    }

    /**
     * @param fromChild 자식이 요청 결과를 알렸다. 아니면 세션 알림이라, 로그인·잠금 해제 화면이 결과를 알리기 전에는 볼트 화면으로 가지 않는다
     * @param offline 잠금 해제를 서버에 닿지 못해 기기에 저장한 볼트로 열었다
     */
    private fun refresh(fromChild: Boolean, offline: Boolean = false) {
        if (!isActive) return
        val router = router ?: return
        val current = router.current
        when (val state = session.state()) {
            is SessionState.Unlocked -> {
                if (current == RootScreen.MAIN) return
                if (!fromChild && current != null) return
                router.routeToMain(offline)
            }
            else -> {
                if (router.isFinishingSession) return
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
