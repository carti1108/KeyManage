package com.keymanage.securestore

enum class BiometricAvailability { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object Cancelled : BiometricResult
    data class Failed(val code: Long, val message: String) : BiometricResult
}

interface BiometricAuthenticator {
    fun availability(): BiometricAvailability
    suspend fun authenticate(reason: String): BiometricResult
}

// 생체 인증을 통과해야만 읽을 수 있는 비밀. 생체 등록이 바뀌면 읽을 수 없게 된다.
// Android Keystore는 인증 필요 키로 암호화할 때도 생체 인증을 요구하므로 저장도 suspend다
interface BiometricSecureStore {
    suspend fun saveProtected(account: String, value: ByteArray)
    suspend fun loadProtected(account: String, reason: String): ByteArray?
    fun delete(account: String)
}

class BiometricAuthenticationException(val result: BiometricResult) :
    Exception("생체 인증 실패: $result")
