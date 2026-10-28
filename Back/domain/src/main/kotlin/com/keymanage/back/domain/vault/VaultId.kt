package com.keymanage.back.domain.vault

/**
 * 볼트 식별자 (api-spec의 vaultId).
 * 파일 경로에도 쓰이므로 UUID 형식만 허용하고, 플랫폼마다 대소문자가 달라도 같은 볼트가 되도록 소문자로 맞춘다 (ADR-0007)
 */
@JvmInline
value class VaultId private constructor(val value: String) {

    companion object {
        // java.util.UUID.fromString은 "1-1-1-1-1" 같은 값도 받아들이므로 자릿수까지 직접 검사한다
        private val UUID_FORMAT = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** @throws InvalidVaultIdException UUID 형식(8-4-4-4-12자리 16진수)이 아닐 때 */
        fun of(raw: String): VaultId {
            if (!UUID_FORMAT.matches(raw)) throw InvalidVaultIdException(raw)
            return VaultId(raw.lowercase())
        }
    }
}
