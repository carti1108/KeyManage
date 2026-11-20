package com.keymanage.feature.messages

import kotlin.test.Test
import kotlin.test.assertEquals

// 마스터 비밀번호 변경·계정 삭제의 안내 본문을 정책(SEC-004, SEC-005) 문구 그대로 고정한다.
// 상수를 거치지 않고 문구 자체를 적어, 문구가 슬며시 바뀌면 이 테스트가 먼저 깨진다
class AccountNoticesTest {
    @Test
    fun 비밀번호_변경_전_안내_본문은_정책_문구_그대로다() {
        assertEquals(
            "비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다.",
            ChangePasswordDialog.BODY,
        )
    }

    @Test
    fun 계정_삭제_전_안내_본문은_정책_문구_그대로다() {
        assertEquals(
            "계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요.",
            DeleteAccountDialog.BODY,
        )
    }

    @Test
    fun 계정_삭제_확인_문구와_완료_안내는_정책_문구_그대로다() {
        assertEquals("계정 삭제", ACCOUNT_DELETION_PHRASE)
        assertEquals("확인을 위해 '계정 삭제'를 입력해 주세요.", DeleteAccountDialog.PHRASE_LABEL)
        assertEquals("계정을 삭제했습니다.", ACCOUNT_DELETED)
    }
}
