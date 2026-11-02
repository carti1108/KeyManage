package com.keymanage.back.config

import com.keymanage.back.domain.auth.AccountRepository
import com.keymanage.back.domain.auth.AuthenticateUseCase
import com.keymanage.back.domain.auth.ChangePasswordUseCase
import com.keymanage.back.domain.auth.CleanUpSessionsUseCase
import com.keymanage.back.domain.auth.DeleteAccountUseCase
import com.keymanage.back.domain.auth.LoginAttemptLimiter
import com.keymanage.back.domain.auth.LoginUseCase
import com.keymanage.back.domain.auth.LogoutUseCase
import com.keymanage.back.domain.auth.PasswordHasher
import com.keymanage.back.domain.auth.PreloginUseCase
import com.keymanage.back.domain.auth.RegisterUseCase
import com.keymanage.back.domain.auth.ServerSecret
import com.keymanage.back.domain.auth.SessionIssuer
import com.keymanage.back.domain.auth.SessionRepository
import com.keymanage.back.domain.vault.CleanUpOrphanVaultsUseCase
import com.keymanage.back.domain.vault.CleanUpPendingVaultDeletionsUseCase
import com.keymanage.back.domain.vault.PendingVaultDeletions
import com.keymanage.back.domain.vault.VaultRepository
import com.keymanage.back.infrastructure.auth.Argon2AuthHashHasher
import com.keymanage.back.infrastructure.auth.FileSystemAccountRepository
import com.keymanage.back.infrastructure.auth.FileSystemPendingVaultDeletions
import com.keymanage.back.infrastructure.auth.FileSystemSessionRepository
import com.keymanage.back.infrastructure.auth.ServerSecretLoader
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path
import java.time.Clock

// 인증 유스케이스에 파일 저장소·Argon2·서버 비밀키를 조립한다 (ADR-0008, SEC-002)
@Configuration
class AuthConfiguration(
    @Value("\${keymanage.auth.storage-dir}") storageDir: String,
) {

    private val root = Path.of(storageDir)

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun accountRepository(): AccountRepository = FileSystemAccountRepository(root.resolve("accounts"))

    @Bean
    fun sessionRepository(): SessionRepository = FileSystemSessionRepository(root.resolve("sessions"))

    // 확정 뒤 지우지 못한 볼트 ID 목록 (ADR-BE-002 결정 1)
    @Bean
    fun pendingVaultDeletions(): PendingVaultDeletions = FileSystemPendingVaultDeletions(root.resolve("pending-vault-deletions.json"))

    @Bean
    fun passwordHasher(): PasswordHasher = Argon2AuthHashHasher()

    // 환경 변수가 없으면 저장 폴더에 첫 실행 때 만든 키를 쓴다
    @Bean
    fun serverSecret(@Value("\${${ServerSecretLoader.ENV_NAME}:}") envValue: String): ServerSecret =
        ServerSecretLoader.load(envValue, root.resolve("server-secret"))

    @Bean
    fun sessionIssuer(sessions: SessionRepository, clock: Clock) = SessionIssuer(sessions, clock)

    @Bean
    fun loginAttemptLimiter(clock: Clock) = LoginAttemptLimiter(clock)

    @Bean
    fun preloginUseCase(accounts: AccountRepository, secret: ServerSecret) = PreloginUseCase(accounts, secret)

    @Bean
    fun registerUseCase(
        accounts: AccountRepository,
        hasher: PasswordHasher,
        issuer: SessionIssuer,
        @Value("\${keymanage.auth.registration-enabled}") registrationEnabled: Boolean,
    ) = RegisterUseCase(accounts, hasher, issuer, registrationEnabled)

    @Bean
    fun loginUseCase(accounts: AccountRepository, hasher: PasswordHasher, issuer: SessionIssuer, limiter: LoginAttemptLimiter) =
        LoginUseCase(accounts, hasher, issuer, limiter)

    @Bean
    fun logoutUseCase(sessions: SessionRepository) = LogoutUseCase(sessions)

    @Bean
    fun changePasswordUseCase(
        accounts: AccountRepository,
        vaults: VaultRepository,
        sessions: SessionRepository,
        hasher: PasswordHasher,
        issuer: SessionIssuer,
        limiter: LoginAttemptLimiter,
        pendingDeletions: PendingVaultDeletions,
    ) = ChangePasswordUseCase(accounts, vaults, sessions, hasher, issuer, limiter, pendingDeletions = pendingDeletions)

    @Bean
    fun deleteAccountUseCase(
        accounts: AccountRepository,
        vaults: VaultRepository,
        sessions: SessionRepository,
        hasher: PasswordHasher,
        limiter: LoginAttemptLimiter,
        pendingDeletions: PendingVaultDeletions,
    ) = DeleteAccountUseCase(accounts, vaults, sessions, hasher, limiter, pendingDeletions)

    // 모든 빈을 만든 뒤, 웹 서버가 요청을 받기 전에 한 번 정리한다. 요청과 겹치면 변경 중인 새 볼트를 고아로 볼 수 있다 (ADR-BE-001 결정 5).
    // 삭제 예정 목록(계정 수와 무관)과 무효 세션도 같은 시점에 정리한다 (ADR-BE-002)
    @Bean
    fun orphanVaultCleanup(
        accounts: AccountRepository,
        vaults: VaultRepository,
        sessions: SessionRepository,
        pendingDeletions: PendingVaultDeletions,
        clock: Clock,
    ) = SmartInitializingSingleton {
        CleanUpPendingVaultDeletionsUseCase(accounts, vaults, pendingDeletions).execute()
        CleanUpOrphanVaultsUseCase(accounts, vaults).execute()
        CleanUpSessionsUseCase(sessions, accounts, clock).execute()
    }

    @Bean
    fun authenticateUseCase(sessions: SessionRepository, accounts: AccountRepository, clock: Clock) =
        AuthenticateUseCase(sessions, accounts, clock)
}
