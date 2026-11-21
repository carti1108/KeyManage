package com.keymanage.domain.session

import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.tabs.TabMessage
import kotlin.coroutines.cancellation.CancellationException

// 계정 삭제 (SEC-005, ADR-0024). 원본: Apps/Web/src/domain/session/deleteAccount.ts
// 비밀번호를 이 기기의 볼트로 확인하고 지금 계정의 KDF 값으로 만든 인증 해시를 서버에 보낸다.
// 성공하면 이 기기의 기록과 탭 기억을 지우고, 같은 브라우저의 다른 탭도 로그아웃시킨다. 자동 잠금 같은 기기 설정은 건드리지 않는다.
// 키·평문·비밀번호는 이 모듈 밖으로 내보내지 않는다

/** 세션 없이 연 볼트라 서버에 연결되지 않음. 계정은 서버에 연결된 상태에서만 지운다 (SEC-005 규칙 1) */
class AccountDeletionOfflineException : Exception("서버에 연결되지 않아 계정을 지울 수 없음")

/** 계정은 서버에서 지웠지만 이 기기의 기록을 지우지 못함. 이 탭은 이미 로그아웃됐다 (SEC-005 사용자 문구) */
class AccountDeletedDeviceNotClearedException : Exception("계정은 지웠지만 기기 기록을 지우지 못함")

/**
 * @throws SessionStateException(잠김·기다리는 사이 로그아웃), AccountDeletionOfflineException, WrongMasterPasswordException(이 기기 확인 또는 서버 AUTH_FAILED),
 *   SessionExpiredException(로그아웃됨), AccountDeletedDeviceNotClearedException(지웠고 로그아웃됨),
 *   그 밖의 ServerError(TOO_MANY_ATTEMPTS 등)·NetworkError(결과를 알 수 없음, SEC-005 예외 2)
 */
internal suspend fun deleteAccount(ctx: SessionContext, password: String) {
    checkRequest(ctx)
    // 앞서 보낸 대기 전송이 끝난 뒤 지운다. 지운 뒤 늦게 나간 저장이 401을 받아 정리 순서가 꼬이지 않게 한다
    ctx.pushChain.await()
    val startedAt = ctx.epoch
    val account = ctx.account
    if (account == null || ctx.unlocked == null) throw SessionStateException("잠금 해제 상태가 아님")
    val accessToken = account.accessToken ?: throw AccountDeletionOfflineException()

    // 지금 계정의 KDF 값으로 키를 만들어 이 기기의 볼트로 먼저 확인한다. 틀리면 서버에 보내지 않는다
    val keys = verifyPassword(password, account).keys
    try {
        if (ctx.epoch != startedAt) throw SessionStateException("기다리는 사이 잠금 또는 로그아웃되어 작업을 취소함")
        val startedGeneration = ctx.generation
        // 요청을 대기 전송 줄에 세워, 그사이 이 탭의 저장이 옛 토큰으로 나가지 않게 한다
        ctx.inPushChain { request(ctx, accessToken, keys.authHash, startedGeneration) }
        // 서버에서는 이미 지워졌으니 완료다. 기다리는 사이 잠갔어도 정리한다.
        // 그사이 로그아웃(다른 탭 등)됐으면 지금 상태는 건드리지 않고, 기기 기록이 지운 계정의 것일 때만 지운다
        if (ctx.generation != startedGeneration) return clearDeletedRecord(ctx, account.email)
        signOutDeleted(ctx)
    } finally {
        keys.authHash.fill(0)
    }
}

private fun checkRequest(ctx: SessionContext) {
    val account = ctx.account
    if (account == null || ctx.unlocked == null) throw SessionStateException("잠금 해제 상태가 아님")
    if (account.accessToken == null) throw AccountDeletionOfflineException()
}

private suspend fun request(ctx: SessionContext, accessToken: String, authHash: ByteArray, startedGeneration: Int) {
    try {
        ctx.deps.auth.deleteAccount(accessToken, authHash)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (error is ServerError && error.code == ServerErrorCode.AUTH_FAILED) throw WrongMasterPasswordException()
        // 토큰이 무효면 다른 곳에서 이미 지웠거나 세션이 끝났다. 세션 만료 흐름을 따른다
        if (isUnauthorized(error) && ctx.generation == startedGeneration) throw expire(ctx)
        throw error
    }
}

/** 기기 기록이 지운 계정의 것이면 지운다. 읽지 못하면 공용 기기에 남지 않도록 지운다 */
private suspend fun clearDeletedRecord(ctx: SessionContext, email: String) {
    val cleared = ctx.deps.tabs.lock.run(TabLockName.DEVICE) {
        val read = try {
            Result.success(readDevice(ctx))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
        val record = read.getOrNull()
        if (read.isSuccess && (record == null || record.email != email)) true else ctx.device.clear()
    }
    if (cleared != true) throw AccountDeletedDeviceNotClearedException()
}

/** 로그인 상태와 탭 기억을 지우고, 이 기기의 기록을 지운 뒤 다른 탭을 로그아웃시킨다 (SEC-005 규칙 5) */
private suspend fun signOutDeleted(ctx: SessionContext) {
    resetSignIn(ctx)
    val cleared = ctx.deps.tabs.lock.run(TabLockName.DEVICE) { ctx.device.clear() } == true
    ctx.deps.tabs.channel.post(TabMessage.LOGGED_OUT)
    if (!cleared) throw AccountDeletedDeviceNotClearedException()
}
