package com.keymanage.android.domain.vault

/** 평문 볼트 ↔ UTF-8 JSON 바이트. 구현은 `:core:data`에 둔다(ADR-AND-001). */
interface VaultSerializer {
    /** 규격(vault-spec 4장)에 맞지 않으면 [VaultFormatError]. */
    fun decode(bytes: ByteArray): VaultData

    /** 쓰기 전에 [VaultRules.validate]로 확인한다. 맞지 않으면 [VaultFormatError]. */
    fun encode(vault: VaultData): ByteArray
}
