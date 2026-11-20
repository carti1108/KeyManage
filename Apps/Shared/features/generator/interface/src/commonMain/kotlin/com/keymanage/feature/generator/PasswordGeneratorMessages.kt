package com.keymanage.feature.generator

/** 생성기 문구 (웹 PROBLEM_MESSAGES와 같다) */
object PasswordGeneratorMessages {
    const val LENGTH_PROBLEM: String = "비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다."
    const val NO_CHARSET_PROBLEM: String = "최소 1개 이상의 문자 종류를 선택해야 합니다."
    /** 원문을 읽어 주지 않고 바뀌었다는 사실만 보조 기술에 알린다 */
    const val REGENERATED_ANNOUNCEMENT: String = "새 비밀번호를 만들었습니다."
    const val COPY_FAILED: String = "클립보드에 복사하지 못했습니다."
}
