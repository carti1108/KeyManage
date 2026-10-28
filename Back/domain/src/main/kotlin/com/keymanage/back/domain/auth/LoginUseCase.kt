package com.keymanage.back.domain.auth

/**
 * 인증 해시를 검증하고 세션을 발급한다 (api-spec 2.5.3, SEC-002).
 *
 * 잠금 확인 -> 해시 검증 -> 실패면 실패 기록 후 거절, 성공이면 기록 초기화 후 발급.
 * 없는 이메일도 가짜 해시로 한 번 검증해 응답 시간으로 계정 존재 여부가 드러나지 않게 한다.
 */
class LoginUseCase(
    private val accounts: AccountRepository,
    private val hasher: PasswordHasher,
    private val issuer: SessionIssuer,
    private val limiter: LoginAttemptLimiter,
) {

    // 첫 요청에서만 해시 시간이 더 걸리지 않도록 미리 만들어 둔다
    private val dummyHash: String = hasher.hash(AuthHash.of(ByteArray(AuthHash.BYTES)))

    /**
     * @throws TooManyAttemptsException 계정이 잠겨 있을 때 (해시 검증을 하지 않음)
     * @throws AuthFailedException 이메일이 없거나 인증 해시가 틀릴 때
     */
    fun execute(email: Email, authHash: AuthHash): IssuedSession {
        limiter.checkAllowed(email)

        val account = accounts.find(email)
        val verified = hasher.verify(authHash, account?.passwordHash ?: dummyHash)
        if (account == null || !verified) {
            limiter.recordFailure(email)
            throw AuthFailedException()
        }

        limiter.recordSuccess(email)
        return issuer.issue(email)
    }
}
