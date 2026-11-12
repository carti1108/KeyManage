package com.keymanage.domain.session

import com.keymanage.domain.auth.ChangedPassword
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.auth.VaultReplacement
import com.keymanage.domain.auth.checkNewMasterPassword
import com.keymanage.domain.crypto.DerivedKeys
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.EncryptionKey
import com.keymanage.domain.crypto.SALT_BYTES
import com.keymanage.domain.crypto.deriveKeys
import com.keymanage.domain.encoding.toBase64
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.vault.ReadOnlyVaultException
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultSyncConflictException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

// 마스터 비밀번호 변경 (SEC-004, ADR-0023). 원본: Apps/Web/src/domain/session/changePassword.ts
// 현재 비밀번호를 이 기기의 볼트로 확인하고, 새 salt·지금 반복 횟수로 새 키를 만들어 화면의 볼트(대기 포함)를 다시 암호화한 뒤
// 서버에 한 요청으로 보낸다. 성공하면 새 토큰·키·KDF 값으로 바꾸고 기기 기록을 탭 간 잠금 안에서 새 값으로 덮어쓴다.
// 키·평문·비밀번호는 이 모듈 밖으로 내보내지 않는다

/** 새 마스터 비밀번호가 SEC-002 길이(12자)보다 짧음 */
class NewMasterPasswordTooShortException : Exception("새 마스터 비밀번호가 너무 짧음")

/** 새 마스터 비밀번호가 지금 비밀번호와 같음 (SEC-004 규칙 2) */
class SameMasterPasswordException : Exception("새 마스터 비밀번호가 지금과 같음")

/** 세션 없이 연 볼트라 서버에 연결되지 않음. 비밀번호는 서버에 연결된 상태에서만 바꾼다 (SEC-004 규칙 1) */
class PasswordChangeOfflineException : Exception("서버에 연결되지 않아 비밀번호를 바꿀 수 없음")

/** 서버에 보낸 새 자격 증명. 응답을 받으면 세션에 반영한다 */
private class NewCredentials(
    val kdf: UnlockKdf,
    val encryptionKey: EncryptionKey,
    /** 보낸 볼트. data는 요청 직전 화면의 볼트이고 payload는 새 키로 암호화한 것이다 */
    val sent: SyncedVault,
    val payload: EncryptedVaultPayload,
)

/**
 * @throws SessionStateException(잠김·기다리는 사이 잠금·로그아웃), ReadOnlyVaultException, PasswordChangeOfflineException,
 *   NewMasterPasswordTooShortException, SameMasterPasswordException, WrongMasterPasswordException(이 기기 확인 또는 서버 AUTH_FAILED),
 *   VaultSyncConflictException, SessionExpiredException(로그아웃됨), 그 밖의 ServerError(TOO_MANY_ATTEMPTS 등)·NetworkError(결과를 알 수 없음, SEC-004 규칙 9)
 */
internal suspend fun changeMasterPassword(ctx: SessionContext, currentPassword: String, newPassword: String) {
    checkRequest(ctx, currentPassword, newPassword)
    // 앞서 보낸 대기 전송이 옛 토큰으로 늦게 401을 받아 이 탭을 로그아웃시키지 않도록 끝나기를 기다린다
    ctx.pushChain.await()
    val startedAt = ctx.epoch
    // 다른 탭이 기기 기록에 남긴 대기 내용을 먼저 합쳐 함께 다시 암호화한다 (ADR-WEB-001)
    persist(ctx)
    ensureCurrent(ctx, startedAt)
    val account = ctx.account
    val opened = ctx.unlocked
    if (account == null || opened == null) throw SessionStateException("잠금 해제 상태가 아님")
    val accessToken = account.accessToken ?: throw PasswordChangeOfflineException()

    val currentKeys = verifyPassword(currentPassword, account).keys
    var newKeys: DerivedKeys? = null
    try {
        ensureCurrent(ctx, startedAt)
        val salt = ctx.deps.random.nextBytes(SALT_BYTES)
        val keys = deriveKeys(newPassword, salt, account.kdf.iterations)
        newKeys = keys
        val kdf = UnlockKdf(toBase64(salt), account.kdf.iterations)
        // 새 토큰은 응답으로 받으므로 암호화에는 토큰 없는 동기화 객체를 쓴다
        val payload = syncFor(ctx, NO_TOKEN, keys.encryptionKey).seal(opened.vault.data)
        ensureCurrent(ctx, startedAt)
        val startedGeneration = ctx.generation
        val creds = NewCredentials(kdf, keys.encryptionKey, opened.vault, payload)
        // 요청부터 반영까지를 대기 전송 줄에 세운다. 그사이 이 탭의 저장이 보내는 전송은 반영 뒤 새 토큰·키로 나가,
        // 옛 토큰으로 401을 받아 성공한 변경을 버리지 않는다
        val pending = ctx.inPushChain {
            // 서버가 바꾼 뒤 취소되면 기기 기록에 옛 KDF 값이 남고 새 토큰도 고아가 된다. 요청부터 반영까지는 취소하지 않는다(요청은 제한 시간 안에 끝난다)
            withContext(NonCancellable) {
                val changed = request(ctx, accessToken, currentKeys.authHash, keys.authHash, kdf, opened.vault.revision, payload)
                adopt(ctx, changed, creds, startedGeneration)
            }
        }
        // 줄 안에서 기다리면 자기 자신을 기다리게 되므로, 대기로 남긴 변경은 줄에서 나온 뒤 보낸다
        if (pending) pushQuietly(ctx)
    } finally {
        currentKeys.authHash.fill(0)
        newKeys?.authHash?.fill(0)
    }
}

private fun checkRequest(ctx: SessionContext, currentPassword: String, newPassword: String) {
    val account = ctx.account
    val opened = ctx.unlocked
    if (account == null || opened == null) throw SessionStateException("잠금 해제 상태가 아님")
    if (opened.vault.readOnly) throw ReadOnlyVaultException()
    if (account.accessToken == null) throw PasswordChangeOfflineException()
    if (checkNewMasterPassword(newPassword, newPassword) != null) throw NewMasterPasswordTooShortException()
    if (newPassword == currentPassword) throw SameMasterPasswordException()
}

private suspend fun request(
    ctx: SessionContext,
    accessToken: String,
    currentAuthHash: ByteArray,
    newAuthHash: ByteArray,
    kdf: UnlockKdf,
    expectedRevision: Int,
    payload: EncryptedVaultPayload,
): ChangedPassword {
    val startedGeneration = ctx.generation
    try {
        return ctx.deps.auth.changePassword(accessToken, currentAuthHash, newAuthHash, kdf, VaultReplacement(expectedRevision, payload))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (error is ServerError && error.code == ServerErrorCode.AUTH_FAILED) throw WrongMasterPasswordException()
        // 그사이 다른 기기가 저장했다. 다음 동기화로 맞춘 뒤 다시 시도한다
        if (error is ServerError && error.code == ServerErrorCode.VAULT_CONFLICT) throw VaultSyncConflictException()
        if (isUnauthorized(error) && ctx.generation == startedGeneration) throw expire(ctx)
        throw error
    }
}

/**
 * 서버에서 바뀐 자격 증명을 세션과 기기 기록에 반영한다. 서버에서는 이미 바뀌었으므로 잠겼어도 반영한다.
 * 요청을 기다리는 사이 이 탭의 볼트가 바뀌었으면 새 키로 다시 암호화해 대기로 둔다
 * @return 대기로 남긴 변경이 있는지
 */
private suspend fun adopt(ctx: SessionContext, changed: ChangedPassword, creds: NewCredentials, startedGeneration: Int): Boolean {
    var vault = SyncedVault(changed.revision, creds.sent.data, readOnly = false, payload = creds.payload)
    var pendingSync = false
    val latest = ctx.unlocked
    if (latest != null && latest.vault !== creds.sent) {
        try {
            val payload = syncFor(ctx, NO_TOKEN, creds.encryptionKey).seal(latest.vault.data)
            vault = SyncedVault(changed.revision, latest.vault.data, readOnly = false, payload = payload)
            pendingSync = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 그사이 고친 볼트를 새 키로 암호화하지 못했다(용량 초과 등). 서버에 올린 볼트로 열고 사유를 알린다
            notifySync(ctx, error)
        }
    }
    val target = ctx.account
    if (target == null || ctx.generation != startedGeneration) {
        // 기다리는 사이 로그아웃·다른 로그인이 있었다. 새 토큰은 폐기하고, 다음 로그인은 새 비밀번호로 한다
        revoke(ctx, changed.accessToken)
        throw SessionStateException("기다리는 사이 로그아웃되어 작업을 취소함")
    }
    // 새 로그인처럼 세대를 올려, 옛 토큰으로 보낸 늦은 요청의 결과(401 등)가 새 세션을 바꾸지 못하게 한다.
    // 옛 키로 암호화하던 저장도 epoch가 바뀌어 취소된다
    ctx.generation += 1
    ctx.epoch += 1
    ctx.retryBlocked = false
    ctx.deps.revisionMemory.raise(changed.revision)
    ctx.account = Account(target.email, changed.accessToken, creds.kdf, vault.payload ?: creds.payload, changed.revision, pendingSync)
    if (ctx.unlocked != null) ctx.unlocked = Unlocked(syncFor(ctx, changed.accessToken, creds.encryptionKey), vault, pendingSync)
    // 옛 KDF 값의 기록은 이 탭이 덮어쓴다. 다른 탭은 바뀐 KDF 값을 보고 로그인 화면으로 간다 (SEC-003 규칙 11, ADR-WEB-001)
    val written = ctx.deps.tabs.lock.run(TabLockName.DEVICE) {
        val current = try {
            readDevice(ctx)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        writeLocked(ctx, current)
    }
    // 탭 기억은 기기 기록과 맞춘다. 기록을 쓰지 못했으면 기록에는 옛 KDF 값이 남으므로 기억도 그대로 둔다.
    // 새로고침하면 옛 기록의 잠금 화면으로 열리고, 잠금 해제 때 서버에서 바뀐 것을 알아 로그인 화면으로 간다
    if (written == true) rememberKdf(ctx, target.email, creds.kdf)
    return pendingSync
}

private suspend fun pushQuietly(ctx: SessionContext) {
    try {
        if (enqueuePush(ctx) == PushResult.SYNCED) notifySync(ctx, null)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // 세션 만료는 expire가 이미 알렸다
        if (error !is SessionExpiredException) notifySync(ctx, error)
    }
}
