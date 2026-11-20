package com.keymanage.domain.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.keymanage.crypto.KeySeparator
import com.keymanage.crypto.PasswordKeyDeriver
import com.keymanage.crypto.PasswordPreprocessor

// 마스터 비밀번호 → 마스터 키 → 암호화 키·인증 해시 (vault-spec 2장, ADR-0008, ADR-0011)
// 원본: Apps/Web/src/domain/crypto/keys.ts. 계산은 crypto 모듈(cryptography-kotlin)이 하고, 여기서는 입력 검사와 키 다루기를 맡는다

const val MIN_ITERATIONS: Int = 600_000

// 서버가 지나치게 큰 값을 내려줘 기기를 오래 멈추게 하는 것도 막는다 (SEC-002)
const val MAX_ITERATIONS: Int = 10_000_000
const val SALT_BYTES: Int = 16

/** 서버가 내려준 KDF 값이 규격보다 약하거나 형식이 틀림. 따르지 않고 로그인을 멈춘다 (SEC-002) */
class InvalidKdfParamsException(message: String) : IllegalArgumentException(message)

/** 짝이 맞지 않는 서로게이트가 있어 플랫폼마다 UTF-8 변환 결과가 달라질 수 있는 비밀번호 */
class InvalidPasswordException(message: String) : IllegalArgumentException(message)

/**
 * 볼트 암·복호화용 AES-256 키. 원본 WebCrypto 키처럼 밖으로 꺼내지 않는다.
 * 바이트는 이 모듈 안에서만 읽고, 문자열로 찍어도 내용이 나오지 않는다
 */
class EncryptionKey internal constructor(private val material: ByteArray) {
    private var wiped = false

    /**
     * 키 바이트. 암호 연산이 시작할 때 한 번 읽는다
     * @throws EncryptionKeyWipedException 잠금으로 지운 뒤. 0으로 덮은 값으로 암호화하는 일을 막는다
     */
    internal val bytes: ByteArray
        get() {
            if (wiped) throw EncryptionKeyWipedException()
            return material
        }

    /** 키를 0으로 덮고 더 쓰지 못하게 한다. 잠그거나 로그아웃할 때 부른다 (SEC-003 규칙 1·6) */
    internal fun wipe() {
        wiped = true
        material.fill(0)
    }

    override fun toString(): String = "EncryptionKey(***)"
}

/** 잠금·로그아웃으로 지운 키를 쓰려 함. 잠그기 전에 시작한 작업이 잠근 뒤 다시 암호화하려 할 때 난다 */
class EncryptionKeyWipedException : IllegalStateException("잠금으로 지운 암호화 키")

class DerivedKeys(
    val encryptionKey: EncryptionKey,
    /** 서버 로그인에 보내는 32바이트 인증 해시. 이것으로 암호화 키를 계산할 수 없다. 보낸 뒤 지운다 */
    val authHash: ByteArray,
)

/**
 * NFC 정규화 → 지정 공백 문자만 앞뒤에서 제거. 중간 공백은 유지한다 (ADR-0011)
 * @throws InvalidPasswordException 짝이 맞지 않는 서로게이트가 있을 때. UTF-8로 바꾸면 U+FFFD로 뭉개져 다른 비밀번호와 같아질 수 있다
 */
fun preprocessPassword(password: String): String {
    if (hasLoneSurrogate(password)) throw InvalidPasswordException("비밀번호에 깨진 유니코드 문자가 있음")
    return PasswordPreprocessor.preprocess(password).decodeToString()
}

/**
 * 키를 만들기 전 KDF 값 검사. 백업 파일처럼 키 유도 전에 미리 거를 때도 쓴다 (SEC-001, VLT-004)
 * @throws InvalidKdfParamsException 반복 횟수가 600,000~10,000,000 밖이거나 salt가 16바이트가 아닐 때
 */
fun assertValidKdf(salt: ByteArray, iterations: Int) {
    if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
        throw InvalidKdfParamsException("반복 횟수는 $MIN_ITERATIONS 이상 $MAX_ITERATIONS 이하여야 함")
    }
    if (salt.size != SALT_BYTES) throw InvalidKdfParamsException("salt는 ${SALT_BYTES}바이트여야 함")
}

/**
 * @throws InvalidKdfParamsException 반복 횟수·salt가 규격 밖일 때. 서버가 약하거나 지나치게 큰 값을 내려주는 것을 막는다
 * @throws InvalidPasswordException 비밀번호에 짝이 맞지 않는 서로게이트가 있을 때
 */
suspend fun deriveKeys(password: String, salt: ByteArray, iterations: Int): DerivedKeys {
    assertValidKdf(salt, iterations)
    if (hasLoneSurrogate(password)) throw InvalidPasswordException("비밀번호에 깨진 유니코드 문자가 있음")
    val passwordBytes = PasswordPreprocessor.preprocess(password)
    val masterKey = try {
        // PBKDF2 60만 회는 JVM·Android·Apple에서 호출한 스레드를 몇 초 막는다. 화면 스레드에서 불러도 멈추지 않게 계산용 스레드로 넘긴다.
        // JS는 WebCrypto가 원래 비동기라 달라지는 것이 없다
        withContext(Dispatchers.Default) { PasswordKeyDeriver.deriveMasterKey(passwordBytes, salt, iterations) }
    } finally {
        // 비밀번호 바이트도 키로 넘긴 뒤 지운다. 문자열 원본은 지울 수 없으므로 화면 쪽에서 오래 들고 있지 않는다
        passwordBytes.fill(0)
    }
    try {
        return DerivedKeys(EncryptionKey(KeySeparator.encryptionKey(masterKey)), KeySeparator.authHash(masterKey))
    } finally {
        // 마스터 키 원본은 나눈 뒤 메모리에서 지운다
        masterKey.fill(0)
    }
}

private fun hasLoneSurrogate(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c.isHighSurrogate() -> if (i + 1 < text.length && text[i + 1].isLowSurrogate()) i++ else return true
            c.isLowSurrogate() -> return true
        }
        i++
    }
    return false
}
