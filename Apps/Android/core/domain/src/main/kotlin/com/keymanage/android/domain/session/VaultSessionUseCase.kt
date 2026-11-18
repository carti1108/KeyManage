package com.keymanage.android.domain.session

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.PasswordPreprocessor
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.AuthApi
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.remote.VaultApi
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.RetryTrigger
import com.keymanage.android.domain.sync.VaultSyncCoordinator
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultSerializer
import java.time.Instant
import java.util.Base64

/**
 * 잠금이 풀린 볼트. [encryptionKey]는 잠글 때 [wipe]로 0으로 덮어쓴다(SEC-003 규칙 1·6).
 * [session]이 null이면 서버 없이 연 상태이고, 동기화하려면 마스터 비밀번호를 다시 받아야 한다(규칙 10·12).
 */
class OpenedVault(
    val account: DeviceAccount,
    val vault: VaultData,
    val session: Session?,
    val encryptionKey: ByteArray,
    /** 세션 없이 연 이유. 다시 입력할 때 보여 준다(규칙 12). */
    val offlineReason: Exception? = null,
) {
    fun wipe() = encryptionKey.fill(0)
}

/** 잠금 해제 실패. */
sealed class UnlockError(message: String) : Exception(message) {
    /** 기기 볼트를 입력한 비밀번호로 열지 못했다. */
    class WrongPassword : UnlockError("마스터 비밀번호가 올바르지 않음")

    /** 서버 KDF 값이 기기 값과 다르거나 로그인이 인증 실패로 거절됐다. 로그인 화면으로 보낸다(규칙 11). */
    class PasswordChanged : UnlockError("마스터 비밀번호가 바뀜")

    /** 잠금을 풀 기기 기록이 없다. 로그인 화면으로 보낸다. */
    class NoDeviceRecord : UnlockError("기기 기록 없음")
}

/**
 * 로그인 뒤 볼트 열기와 잠금 해제(SEC-003, VLT-002, VLT-003).
 * 키 유도와 통신이 있으므로 메인 스레드가 아닌 곳에서 부른다.
 */
class VaultSessionUseCase(
    private val authApi: AuthApi,
    private val vaultApi: VaultApi,
    private val deriver: MasterKeyDeriver,
    private val separator: KeySeparator,
    private val cipher: VaultCipher,
    private val serializer: VaultSerializer,
    private val coordinator: VaultSyncCoordinator,
    private val clock: () -> Instant = Instant::now,
) {
    /** 재시작 뒤 잠금 화면으로 열 계정(규칙 9). 기기 기록이 없거나 읽지 못하면 null. */
    fun lockedAccount(): DeviceAccount? = coordinator.restore()?.account

    /** 가입·로그인 직후 볼트를 연다. 볼트가 없으면 빈 볼트를 저장하고(규칙 5), 대기 내용은 병합해 올린다(VLT-002 규칙 10). */
    fun openAfterAuth(auth: AuthResult): OpenedVault {
        coordinator.onAuthenticated(auth.account)
        val vault = openOnline(auth.account, auth.session, auth.encryptionKey)
        return OpenedVault(auth.account, vault, auth.session, auth.encryptionKey)
    }

    /**
     * 마스터 비밀번호로 잠금을 푼다(규칙 4). 기기 볼트 복호화 성공 여부로 확인하므로 오프라인에서도 열린다.
     * [session]이 없으면 인증 해시로 다시 로그인하고, 서버에 닿지 못하면 세션 없이 연다(규칙 10·12).
     */
    fun unlock(password: String, session: Session?): OpenedVault {
        val record = coordinator.restore() ?: throw UnlockError.NoDeviceRecord()
        val account = record.account
        val kdf = KdfParameters(Base64.getDecoder().decode(account.kdfSalt), account.kdfIterations)
        val keys = deriveKeys(password, kdf)
        try {
            val local = try {
                serializer.decode(cipher.decrypt(record.payload, keys.encryptionKey))
            } catch (error: CryptoError.AuthenticationFailed) {
                keys.encryptionKey.fill(0)
                if (serverKdfDiffers(account)) throw UnlockError.PasswordChanged()
                throw UnlockError.WrongPassword()
            }
            coordinator.onAuthenticated(account)
            var offlineReason: Exception? = null
            val activeSession = session ?: try {
                resume(account, keys.authHash)
            } catch (error: UnlockError) {
                keys.encryptionKey.fill(0)
                throw error
            } catch (error: ApiError) {
                offlineReason = error
                null
            }
            val vault = if (activeSession == null) {
                local
            } else {
                try {
                    openOnline(account, activeSession, keys.encryptionKey)
                } catch (error: ApiError) {
                    offlineReason = error
                    local
                }
            }
            return OpenedVault(account, vault, activeSession, keys.encryptionKey, offlineReason)
        } finally {
            keys.authHash.fill(0)
        }
    }

    /**
     * 로그아웃. 서버 세션을 끊고(실패해도 진행) 기기 기록을 지운다(VLT-003 규칙 4).
     * 기기 기록을 지우지 못하면 [com.keymanage.android.domain.sync.DeviceStorageError]를 던진다(규칙 8).
     */
    fun logout(session: Session?) {
        if (session != null) {
            try {
                authApi.logout(session.accessToken)
            } catch (error: ApiError) {
                // 서버 세션은 만료로 끝난다. 기기 기록 삭제가 우선이다
            }
        }
        coordinator.logout()
    }

    private fun openOnline(account: DeviceAccount, session: Session, key: ByteArray): VaultData {
        val token = session.accessToken
        val remote = vaultApi.fetchVault(token)
        if (remote == null) {
            coordinator.open(account, 0)
            if (coordinator.retryPending(key, token, RetryTrigger.RELOGIN) == null) {
                coordinator.save(VaultData(VaultData.SUPPORTED_VERSION, clock(), emptyList()), key, token)
            }
            return checkNotNull(coordinator.latestVault)
        }
        val server = serializer.decode(cipher.decrypt(remote.payload, key))
        coordinator.open(account, remote.revision)
        if (coordinator.hasPending) {
            coordinator.retryPending(key, token, RetryTrigger.RELOGIN)
            return coordinator.latestVault ?: server
        }
        coordinator.keepServerCopy(server, key)
        return server
    }

    /** 기기에 둔 KDF 값으로 만든 인증 해시로 로그인한다. 인증 해시는 이 요청 밖에 남기지 않는다(규칙 10·11). */
    private fun resume(account: DeviceAccount, authHash: ByteArray): Session {
        if (serverKdfDiffersOrThrow(account)) throw UnlockError.PasswordChanged()
        try {
            return authApi.login(account.email, authHash)
        } catch (error: ApiError.Server) {
            if (error.code == ServerErrorCode.AUTH_FAILED) throw UnlockError.PasswordChanged()
            throw error
        }
    }

    /** 서버에 닿지 못하면 false(판정 보류). */
    private fun serverKdfDiffers(account: DeviceAccount): Boolean = try {
        serverKdfDiffersOrThrow(account)
    } catch (error: ApiError) {
        false
    }

    private fun serverKdfDiffersOrThrow(account: DeviceAccount): Boolean {
        val info = authApi.prelogin(account.email)
        return info.kdf != SUPPORTED_KDF || info.iterations != account.kdfIterations ||
            Base64.getEncoder().encodeToString(info.salt) != account.kdfSalt
    }

    private fun deriveKeys(password: String, kdf: KdfParameters): com.keymanage.android.domain.crypto.SeparatedKeys {
        val passwordBytes = try {
            PasswordPreprocessor.preprocess(password)
        } catch (error: CryptoError.InvalidPassword) {
            throw UnlockError.WrongPassword()
        }
        val masterKey = try {
            deriver.deriveMasterKey(passwordBytes, kdf)
        } catch (error: CryptoError.InvalidPassword) {
            throw UnlockError.WrongPassword()
        } finally {
            passwordBytes.fill(0)
        }
        try {
            return separator.separate(masterKey)
        } finally {
            masterKey.fill(0)
        }
    }

    private companion object {
        const val SUPPORTED_KDF = "PBKDF2-HMAC-SHA256"
    }
}
