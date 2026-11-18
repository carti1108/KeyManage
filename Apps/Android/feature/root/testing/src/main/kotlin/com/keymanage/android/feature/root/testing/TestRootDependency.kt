package com.keymanage.android.feature.root.testing

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.session.RestoredBackup
import com.keymanage.android.domain.session.SavedVault
import com.keymanage.android.domain.sync.SaveOutcome
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.root.VaultSessionGateway
import java.time.Instant

/** 테스트용 세션 게이트웨이. 정한 결과를 돌려주고 호출을 기록한다. */
class FakeVaultSessionGateway(
    var lockedEmail: String? = null,
    var open: (AuthResult) -> OpenedVault = { error("openAfterAuth 결과를 정하지 않음") },
) : VaultSessionGateway {
    val logouts = mutableListOf<Session?>()
    var logoutFailure: Exception? = null

    override fun lockedAccountEmail() = lockedEmail

    override fun openAfterAuth(result: AuthResult) = open(result)

    val saved = mutableListOf<VaultData>()

    override fun save(opened: OpenedVault, vault: VaultData): SavedVault {
        saved += vault
        return SavedVault(vault, SaveOutcome.Synced(saved.size))
    }

    val restoreCalls = mutableListOf<String>()

    override fun exportBackup(opened: OpenedVault, vault: VaultData, now: Instant) = "backup:${vault.items.size}".toByteArray()

    override fun restoreBackup(opened: OpenedVault, current: VaultData, bytes: ByteArray, password: String, now: Instant): RestoredBackup {
        restoreCalls += password
        return RestoredBackup(1, save(opened, current))
    }

    override fun logout(session: Session?) {
        logouts += session
        logoutFailure?.let { throw it }
    }
}
