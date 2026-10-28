package com.keymanage.back.domain.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

/** 테스트용 계정 저장소. 같은 이메일 생성은 한 번만 성공한다 */
class InMemoryAccountRepository : AccountRepository {

    private val accounts = mutableMapOf<Email, Account>()

    @Synchronized
    override fun find(email: Email): Account? = accounts[email]

    @Synchronized
    override fun createIfAbsent(account: Account): Boolean {
        if (account.email in accounts) return false
        accounts[account.email] = account
        return true
    }
}

class InMemorySessionRepository : SessionRepository {

    val sessions = mutableMapOf<String, Session>()

    override fun save(session: Session) {
        sessions[session.tokenHash] = session
    }

    override fun find(tokenHash: String): Session? = sessions[tokenHash]

    override fun delete(tokenHash: String) {
        sessions.remove(tokenHash)
    }
}

/** 실제 Argon2 대신 쓰는 해시. 검증 호출 횟수를 세어 없는 이메일에도 검증을 수행하는지 확인한다 */
class FakePasswordHasher : PasswordHasher {

    var verifyCalls = 0
        private set

    override fun hash(authHash: AuthHash): String = "fake:" + Base64.getEncoder().encodeToString(authHash.bytes())

    override fun verify(authHash: AuthHash, encoded: String): Boolean {
        verifyCalls++
        return hash(authHash) == encoded
    }
}

/** 시간을 앞으로 돌릴 수 있는 시계 */
class MutableClock(private var now: Instant = Instant.parse("2026-10-28T00:00:00Z")) : Clock() {

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun instant(): Instant = now

    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?): Clock = this
}

fun authHashOf(fill: Int): AuthHash = AuthHash.of(ByteArray(AuthHash.BYTES) { fill.toByte() })

fun kdfOf(fill: Int): KdfParams = KdfParams(ByteArray(KdfParams.SALT_BYTES) { fill.toByte() }, KdfParams.MIN_ITERATIONS)
