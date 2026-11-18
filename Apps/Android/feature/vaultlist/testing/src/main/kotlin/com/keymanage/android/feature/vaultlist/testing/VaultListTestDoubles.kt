package com.keymanage.android.feature.vaultlist.testing

import com.keymanage.android.feature.vaultlist.VaultListListener

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
