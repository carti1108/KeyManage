package com.keymanage.feature.generator.testing

/**
 * 비밀번호 생성기를 한 가지 상황으로 띄우는 시나리오. Android·iOS·웹의 Example 앱이 같은 목록을 보여 준다 (modularization-plan 3장 3번)
 *
 * @param allowCopy 복사 버튼을 둘지(생성기 탭)
 * @param insideForm 항목 폼 안에서 쓰는 경우. 부모가 결과를 받으므로 사용·닫기 버튼이 생긴다
 */
enum class PasswordGeneratorScenario(val title: String, val allowCopy: Boolean, val insideForm: Boolean) {
    TAB("생성기 탭 (복사)", allowCopy = true, insideForm = false),
    COPY_FAILS("생성기 탭 (복사 실패)", allowCopy = true, insideForm = false),
    INSIDE_FORM("항목 폼 안 (사용·닫기)", allowCopy = false, insideForm = true),
    ;

    /** 이 상황을 만드는 가짜 클립보드 */
    fun clipboard(): FakeGeneratorClipboard = FakeGeneratorClipboard().also { clipboard ->
        if (this == COPY_FAILS) clipboard.failure = IllegalStateException("클립보드를 쓸 수 없음")
    }
}
