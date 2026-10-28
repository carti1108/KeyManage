package com.keymanage.back.domain.auth

/**
 * 로그인 전에 마스터 키 유도 값을 돌려준다 (api-spec 2.5.1, SEC-002).
 * 없는 이메일에도 서버 비밀키 HMAC으로 만든 가짜 salt를 같은 이메일에 항상 같게 돌려줘 계정 존재 여부를 숨긴다
 */
class PreloginUseCase(
    private val accounts: AccountRepository,
    private val secret: ServerSecret,
) {

    fun execute(email: Email): KdfParams = accounts.find(email)?.kdf ?: fakeKdf(email)

    private fun fakeKdf(email: Email): KdfParams {
        val salt = secret.hmac("keymanage-prelogin:${email.value}".toByteArray(Charsets.UTF_8)).copyOf(KdfParams.SALT_BYTES)
        return KdfParams(salt, KdfParams.MIN_ITERATIONS)
    }
}
