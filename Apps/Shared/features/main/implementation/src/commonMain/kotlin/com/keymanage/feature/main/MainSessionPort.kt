package com.keymanage.feature.main

import com.keymanage.feature.SessionPort

/**
 * 계정 삭제를 서버가 받아들인 뒤에는 이 탭이 먼저 로그인 전 상태로 바뀌어 잠금 알림이 나가고, 그 뒤에 기기 기록을 지운다.
 * 알림을 받은 부모가 이 화면을 내려 진행 중인 작업을 취소하면 기록이 남으므로, 진행 중인지 이 포트가 센다
 */
class MainSessionPort(private val delegate: SessionPort) : SessionPort by delegate {
    var deletesInFlight = 0
        private set

    override suspend fun deleteAccount(password: String) {
        deletesInFlight += 1
        try {
            delegate.deleteAccount(password)
        } finally {
            deletesInFlight -= 1
        }
    }
}
