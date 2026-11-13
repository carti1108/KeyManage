package com.keymanage.websession

import com.keymanage.domain.session.SessionState
import com.keymanage.domain.vault.encodeVaultData

/** 화면이 읽는 세션 상태. 볼트는 encodeVaultData JSON 문자열이다(웹 parseVaultData로 읽는다) */
@JsExport
class JsSessionState internal constructor(
    /** 'signedOut' | 'locked' | 'unlocked' */
    val status: String,
    val email: String?,
    val pendingSync: Boolean,
    val signedIn: Boolean,
    val storedOnDevice: Boolean,
    val revision: Int,
    val readOnly: Boolean,
    val vaultJson: String?,
    /** SyncedVault.payload(EncryptedVaultPayload)의 JSON. 없으면 null */
    val payloadJson: String?,
)

internal fun SessionState.toJs(): JsSessionState = when (this) {
    SessionState.SignedOut -> JsSessionState("signedOut", null, false, false, false, 0, false, null, null)
    is SessionState.Locked -> JsSessionState("locked", email, pendingSync, false, false, 0, false, null, null)
    is SessionState.Unlocked -> JsSessionState(
        "unlocked",
        email,
        pendingSync,
        signedIn,
        storedOnDevice,
        vault.revision,
        vault.readOnly,
        encodeVaultData(vault.data),
        vault.payload?.toJson()?.toString(),
    )
}
