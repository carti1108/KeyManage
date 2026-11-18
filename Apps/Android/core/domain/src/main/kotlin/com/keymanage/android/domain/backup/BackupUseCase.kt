package com.keymanage.android.domain.backup

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.PasswordPreprocessor
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * 암호화 백업 내보내기와 열기·복원(VLT-004). 모든 처리는 기기 안에서 하고 서버에 보내지 않는다(규칙 11).
 * 복원 결과 저장은 호출자가 일반 수정처럼 동기화 조정자로 넘긴다(규칙 9).
 */
class BackupUseCase(
    private val deriver: MasterKeyDeriver,
    private val separator: KeySeparator,
    private val cipher: VaultCipher,
    private val serializer: VaultSerializer,
    private val codec: BackupCodec,
) {
    /**
     * 화면에 보이는 볼트(대기 중인 변경 포함)를 지금 키로 새로 암호화해 파일 바이트로 만든다(규칙 2).
     * @param kdf 현재 계정의 KDF 값. 파일은 이 값과 내보낸 시점의 마스터 비밀번호로 열린다.
     */
    fun export(vault: VaultData, encryptionKey: ByteArray, kdf: KdfParameters, now: Instant): ByteArray {
        val plaintext = serializer.encode(vault)
        try {
            val payload = cipher.encrypt(plaintext, encryptionKey)
            return codec.encode(BackupFile(now, kdf.iterations, Base64.getEncoder().encodeToString(kdf.salt), payload))
        } finally {
            plaintext.fill(0)
        }
    }

    /** 파일의 KDF 값으로 키를 만들어 복호화한다(규칙 5). 다른 계정의 백업도 비밀번호가 맞으면 열린다. */
    fun open(bytes: ByteArray, password: String): VaultData {
        val file = codec.decode(bytes)
        val kdf = kdfOf(file)
        val encryptionKey = deriveEncryptionKey(password, kdf)
        val plaintext = try {
            cipher.decrypt(file.vault, encryptionKey)
        } catch (error: CryptoError.AuthenticationFailed) {
            throw BackupError.CannotOpen(error)
        } catch (error: CryptoError) {
            throw BackupError.NotBackupFile("볼트 암호문 형식 오류", error)
        } finally {
            encryptionKey.fill(0)
        }
        try {
            val vault = serializer.decode(plaintext)
            if (vault.isReadOnly) throw BackupError.NewerVersion("모르는 볼트 스키마 버전: ${vault.version}")
            return vault
        } finally {
            plaintext.fill(0)
        }
    }

    /** 백업을 열어 지금 볼트에 되돌린다. 읽기 전용 볼트에는 복원하지 않는다(규칙 10). */
    fun restore(current: VaultData, bytes: ByteArray, password: String, now: Instant): RestoreResult {
        if (current.isReadOnly) throw SyncError.ReadOnlyVault(current.version)
        return VaultRestorer.restore(current, open(bytes, password), now)
    }

    private fun kdfOf(file: BackupFile): KdfParameters = try {
        KdfParameters(Base64.getDecoder().decode(file.kdfSalt), file.kdfIterations)
    } catch (error: IllegalArgumentException) {
        throw BackupError.NotBackupFile("kdf.salt가 Base64가 아님", error)
    } catch (error: CryptoError.InvalidKdfParameters) {
        throw BackupError.NotBackupFile("KDF 값이 범위 밖", error)
    }

    private fun deriveEncryptionKey(password: String, kdf: KdfParameters): ByteArray {
        val passwordBytes = try {
            PasswordPreprocessor.preprocess(password)
        } catch (error: CryptoError.InvalidPassword) {
            throw BackupError.CannotOpen(error)
        }
        val masterKey = try {
            deriver.deriveMasterKey(passwordBytes, kdf)
        } catch (error: CryptoError.InvalidPassword) {
            throw BackupError.CannotOpen(error)
        } finally {
            passwordBytes.fill(0)
        }
        try {
            val keys = separator.separate(masterKey)
            keys.authHash.fill(0)
            return keys.encryptionKey
        } finally {
            masterKey.fill(0)
        }
    }

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

        /** 기기 현지 날짜로 만든 파일 이름(VLT-004 값 표). */
        fun fileName(localDate: LocalDate): String = "keymanage-backup-${DATE.format(localDate)}.json"
    }
}
