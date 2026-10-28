package com.keymanage.back.domain.vault

/**
 * 클라이언트가 암호화한 볼트 페이로드 (vault-spec 3장).
 * 서버는 내용을 해석하지 않고 그대로 저장·반환한다 (ADR-0001)
 */
data class EncryptedVault(val payload: String) {

    /** 용량 제한은 요청 페이로드 JSON의 바이트 수로 잰다 (VLT-001) */
    val sizeInBytes: Int get() = payload.toByteArray(Charsets.UTF_8).size
}
