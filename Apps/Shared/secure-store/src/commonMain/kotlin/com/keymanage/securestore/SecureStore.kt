package com.keymanage.securestore

// 기기 밖으로 나가면 안 되는 비밀(생체 인증용 키, 세션 토큰)을 담는 저장소
interface SecureStore {
    fun save(account: String, value: ByteArray)
    fun load(account: String): ByteArray?
    fun delete(account: String)
}

class SecureStoreException(val status: Int, operation: String) :
    Exception("보안 저장소 $operation 실패 (status=$status)")
