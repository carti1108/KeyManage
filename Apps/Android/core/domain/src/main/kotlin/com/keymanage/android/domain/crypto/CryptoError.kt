package com.keymanage.android.domain.crypto

/** 암호 처리 실패. 화면 문구는 COM-002에 따라 상위 계층이 정한다. */
sealed class CryptoError(message: String) : Exception(message) {
    /** 전처리할 수 없는 비밀번호(짝 없는 서로게이트 등). */
    class InvalidPassword(message: String) : CryptoError(message)

    /** salt 길이나 반복 횟수가 vault-spec 범위를 벗어남(SEC-002). */
    class InvalidKdfParameters(message: String) : CryptoError(message)

    /** 키·IV·태그 길이나 Base64 형식이 맞지 않음. */
    class InvalidInput(message: String) : CryptoError(message)

    /** 인증 태그 검증 실패. 마스터 키가 다르거나 암호문·헤더가 바뀌었다. */
    class AuthenticationFailed : CryptoError("인증 태그 검증 실패")
}
