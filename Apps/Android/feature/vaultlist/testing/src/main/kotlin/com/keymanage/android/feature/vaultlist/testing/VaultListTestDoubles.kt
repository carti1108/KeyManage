package com.keymanage.android.feature.vaultlist.testing

import com.keymanage.android.domain.clipboard.ClipboardPort
import com.keymanage.android.domain.clipboard.Scheduler
import com.keymanage.android.domain.session.SavedVault
import com.keymanage.android.domain.sync.SaveOutcome
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.vaultlist.VaultListListener
import com.keymanage.android.feature.vaultlist.VaultSaver

class RecordingVaultListListener : VaultListListener {
    var lockRequests = 0
        private set
    var resyncRequests = 0
        private set

    override fun onLockRequested() {
        lockRequests++
    }

    override fun onResyncRequested() {
        resyncRequests++
    }
}

/** 받은 볼트를 그대로 저장된 것으로 돌려주거나 [failure]를 던진다. */
class RecordingVaultSaver : VaultSaver {
    val saved = mutableListOf<VaultData>()
    var failure: Exception? = null

    override fun save(vault: VaultData): SavedVault {
        failure?.let { throw it }
        saved += vault
        return SavedVault(vault, SaveOutcome.Synced(saved.size))
    }
}

/** 메모리 클립보드. [failCopy]면 복사에 실패한다. */
class FakeClipboard : ClipboardPort {
    var content = ""
    var failCopy = false

    override fun copySecret(text: String) {
        check(!failCopy) { "복사 실패" }
        content = text
    }

    override fun clear() {
        content = ""
    }
}

/** 실행하지 않고 쌓아 두는 스케줄러. */
class HeldScheduler : Scheduler {
    var scheduled = 0
        private set

    override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
        scheduled++
        return {}
    }
}
