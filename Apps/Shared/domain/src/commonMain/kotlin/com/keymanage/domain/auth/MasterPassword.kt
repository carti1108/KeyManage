package com.keymanage.domain.auth

// 가입할 때 새 마스터 비밀번호 검사 (SEC-002). 서버는 비밀번호를 볼 수 없으므로 클라이언트가 막는다
// 원본: Apps/Web/src/domain/auth/masterPassword.ts

const val MIN_MASTER_PASSWORD_LENGTH: Int = 12

enum class NewMasterPasswordProblem { TOO_SHORT, MISMATCH }

/** @return 문제가 없으면 null. 길이 부족을 불일치보다 먼저 알린다 */
fun checkNewMasterPassword(password: String, confirmation: String): NewMasterPasswordProblem? {
    // 이모지처럼 UTF-16 두 단위인 문자도 한 글자로 센다. 앞뒤 공백도 비밀번호의 일부다
    val codePoints = password.length - password.indices.count { i ->
        i > 0 && password[i].isLowSurrogate() && password[i - 1].isHighSurrogate()
    }
    if (codePoints < MIN_MASTER_PASSWORD_LENGTH) return NewMasterPasswordProblem.TOO_SHORT
    if (password != confirmation) return NewMasterPasswordProblem.MISMATCH
    return null
}
