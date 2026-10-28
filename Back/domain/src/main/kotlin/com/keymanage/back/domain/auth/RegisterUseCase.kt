package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId
import java.util.UUID

/** 계정을 만들고 볼트 ID를 발급한 뒤 바로 로그인 세션을 돌려준다 (api-spec 2.5.2, SEC-002) */
class RegisterUseCase(
    private val accounts: AccountRepository,
    private val hasher: PasswordHasher,
    private val issuer: SessionIssuer,
    private val registrationEnabled: Boolean,
) {

    /**
     * @throws RegistrationDisabledException 서버 설정으로 가입이 꺼져 있을 때
     * @throws EmailAlreadyRegisteredException 이미 가입된 이메일일 때 (기존 계정은 바뀌지 않음)
     */
    fun execute(email: Email, authHash: AuthHash, kdf: KdfParams): IssuedSession {
        if (!registrationEnabled) throw RegistrationDisabledException()

        val account = Account(email, kdf, hasher.hash(authHash), VaultId.of(UUID.randomUUID().toString()))
        if (!accounts.createIfAbsent(account)) throw EmailAlreadyRegisteredException()
        return issuer.issue(email)
    }
}
