package com.keymanage.android.feature.unlock.testing

import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.feature.unlock.BiometricUnlocker
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.feature.unlock.Unlocker

/** 정한 결과를 돌려주거나 [failure]를 던지는 Unlocker. 받은 비밀번호·세션을 기록한다. */
class StubUnlocker(var result: () -> OpenedVault) : Unlocker {
    var failure: Exception? = null
    val calls = mutableListOf<Pair<String, Session?>>()

    override fun unlock(password: String, session: Session?): OpenedVault {
        calls += password to session
        failure?.let { throw it }
        return result()
    }

    val acceptedRollbacks = mutableListOf<Int>()

    override fun acceptRollback(serverRevision: Int) {
        acceptedRollbacks += serverRevision
    }
}

/** 정한 결과를 돌려주거나 [failure]를 던지는 생체 해제. 받은 세션을 기록한다. */
class StubBiometricUnlocker(var result: () -> OpenedVault) : BiometricUnlocker {
    var available = true
    var failure: Exception? = null
    val sessions = mutableListOf<Session?>()

    override fun isAvailable() = available

    override suspend fun unlock(session: Session?): OpenedVault {
        sessions += session
        failure?.let { throw it }
        return result()
    }
}

class RecordingUnlockListener : UnlockListener {
    val unlocked = mutableListOf<OpenedVault>()
    val loginRequired = mutableListOf<String?>()
    var logoutRequests = 0
        private set

    override fun onUnlocked(opened: OpenedVault) {
        unlocked += opened
    }

    override fun onLoginRequired(message: String?) {
        loginRequired += message
    }

    override fun onLogoutRequested() {
        logoutRequests++
    }
}
