package com.keymanage.feature.messages

// 생체 해제 문구 (SEC-003 규칙 4·8)
// 원본: Apps/Android feature/unlock의 UnlockMessages, feature/settings의 SettingsMessages

object BiometricUnlockMessages {
    /** 잠금 화면 버튼과 설정 스위치의 이름 */
    const val ACTION = "생체 인증으로 잠금 해제"

    /** 마지막 마스터 비밀번호 해제 뒤 14일이 지났다 */
    const val EXPIRED = "보안을 위해 14일마다 마스터 비밀번호를 입력해야 합니다."

    /** 생체 정보가 새로 등록됐거나 기기 암호를 꺼서 감싼 키가 무효다. 생체 해제는 꺼졌다 */
    const val INVALIDATED = "생체 정보가 바뀌어 생체 인증을 껐습니다. 마스터 비밀번호로 잠금을 풀어 주세요."

    /** 잠금 화면에서 지금은 생체 인증을 쓸 수 없다 */
    const val UNAVAILABLE = "지금은 생체 인증을 쓸 수 없습니다. 마스터 비밀번호를 입력해 주세요."

    /** 세션이 만료돼 생체 해제 대신 마스터 비밀번호를 받는다 (SEC-003 규칙 8) */
    const val PASSWORD_REQUIRED = "보안을 위해 마스터 비밀번호를 입력해 주세요."

    /** 설정에서 켜려 했지만 기기에 생체 정보나 화면 잠금이 없다 */
    const val SETTING_UNAVAILABLE = "이 기기에서는 생체 인증을 쓸 수 없습니다. 생체 정보와 화면 잠금을 등록해 주세요."
}

/** 생체 해제 실패를 잠금 화면 문구로 바꾼다. 취소는 문구가 없다 */
fun biometricUnlockMessage(error: com.keymanage.domain.lock.BiometricError): String? = when (error) {
    is com.keymanage.domain.lock.BiometricError.Cancelled -> null
    is com.keymanage.domain.lock.BiometricError.Expired -> BiometricUnlockMessages.EXPIRED
    is com.keymanage.domain.lock.BiometricError.PasswordRequired -> BiometricUnlockMessages.PASSWORD_REQUIRED
    is com.keymanage.domain.lock.BiometricError.Unavailable -> BiometricUnlockMessages.UNAVAILABLE
    is com.keymanage.domain.lock.BiometricError.Invalidated, is com.keymanage.domain.lock.BiometricError.Disabled -> BiometricUnlockMessages.INVALIDATED
}
