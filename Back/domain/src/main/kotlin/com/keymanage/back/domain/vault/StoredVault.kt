package com.keymanage.back.domain.vault

/** 저장소에 보관된 볼트와 그 revision */
data class StoredVault(val id: VaultId, val revision: Revision, val vault: EncryptedVault)
