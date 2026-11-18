package com.keymanage.android

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import com.keymanage.android.data.backup.JsonBackupCodec
import com.keymanage.android.data.clipboard.AndroidClipboard
import com.keymanage.android.data.clipboard.MainLooperScheduler
import com.keymanage.android.data.crypto.AesGcmVaultCipher
import com.keymanage.android.data.lock.PreferencesAutoLockStore
import com.keymanage.android.data.crypto.HkdfKeySeparator
import com.keymanage.android.data.crypto.Pbkdf2MasterKeyDeriver
import com.keymanage.android.data.remote.HttpApiClient
import com.keymanage.android.data.sync.FileDeviceRecordStore
import com.keymanage.android.data.vault.JsonVaultSerializer
import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.backup.BackupUseCase
import com.keymanage.android.domain.auth.AuthUseCase
import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.clipboard.Scheduler
import com.keymanage.android.domain.generator.PasswordGenerator
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.AccountUseCase
import com.keymanage.android.domain.session.BackupSession
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.session.VaultSessionUseCase
import com.keymanage.android.domain.sync.VaultSyncCoordinator
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthDependency
import com.keymanage.android.feature.auth.impl.AuthBuilder
import com.keymanage.android.feature.generator.GeneratorDependency
import com.keymanage.android.feature.generator.impl.GeneratorBuilder
import com.keymanage.android.feature.main.MainDependency
import com.keymanage.android.feature.main.impl.MainBuilder
import com.keymanage.android.feature.root.AppEventListener
import com.keymanage.android.feature.root.AppEvents
import com.keymanage.android.feature.root.RootDependency
import com.keymanage.android.feature.root.VaultSessionGateway
import com.keymanage.android.feature.root.impl.RootBuilder
import com.keymanage.android.feature.settings.PendingChanges
import com.keymanage.android.feature.settings.SettingsDependency
import com.keymanage.android.feature.settings.impl.SettingsBuilder
import com.keymanage.android.feature.unlock.UnlockDependency
import com.keymanage.android.feature.unlock.Unlocker
import com.keymanage.android.feature.unlock.impl.UnlockBuilder
import com.keymanage.android.feature.vaultlist.VaultListDependency
import com.keymanage.android.feature.vaultlist.impl.VaultListBuilder
import com.uber.rib.core.RibActivity
import com.uber.rib.core.ViewRouter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** 앱의 유일한 Activity. Root RIB 트리를 만들어 붙인다. */
class MainActivity : RibActivity() {
    private val events = ActivityEvents()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 앱 전환 화면·화면 녹화에 볼트가 비치지 않게 한다(SEC-003 규칙 3)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }

    override fun createRouter(parentViewGroup: ViewGroup): ViewRouter<*, *> =
        RootBuilder(AppRootDependency(applicationContext, events)).build(parentViewGroup)

    override fun onStart() {
        super.onStart()
        events.listener?.onForeground()
    }

    override fun onStop() {
        events.listener?.onBackground()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        events.listener?.onUserInteraction()
    }
}

/** Activity 생명주기와 조작을 Root에 넘긴다. */
private class ActivityEvents : AppEvents {
    var listener: AppEventListener? = null
        private set

    override fun setListener(listener: AppEventListener?) {
        this.listener = listener
    }
}

/** 앱 조립. 포트 구현(:core:data)을 피처 의존성에 넘기는 곳은 여기뿐이다(ADR-AND-001). */
private class AppRootDependency(context: Context, override val appEvents: AppEvents) : RootDependency {
    private val api by lazy { HttpApiClient(BuildConfig.SERVER_URL) }
    private val deriver = Pbkdf2MasterKeyDeriver()
    private val separator = HkdfKeySeparator()
    private val cipher = AesGcmVaultCipher()
    private val serializer = JsonVaultSerializer()
    private val store = FileDeviceRecordStore(File(context.noBackupFilesDir, "device-record.json"))
    private val backupSession by lazy {
        BackupSession(BackupUseCase(deriver, separator, cipher, serializer, JsonBackupCodec())) { opened, vault -> sessions.save(opened, vault) }
    }
    private val accounts by lazy {
        AccountUseCase(api, deriver, separator, cipher, serializer, coordinator)
    }
    private val coordinator by lazy { VaultSyncCoordinator(api, cipher, serializer, store) }
    private val sessions by lazy {
        VaultSessionUseCase(api, api, deriver, separator, cipher, serializer, coordinator)
    }

    override val workDispatcher: CoroutineDispatcher = Dispatchers.Default

    override val clipboard: ClipboardBurner = run {
        lateinit var burner: ClipboardBurner
        val port = AndroidClipboard(context.getSystemService(ClipboardManager::class.java)) { burner.onExternalChange() }
        burner = ClipboardBurner(port, scheduler)
        burner
    }

    override val autoLockStore = PreferencesAutoLockStore(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    override val scheduler: Scheduler = MainLooperScheduler()
    override val monotonicClock: () -> Long = SystemClock::elapsedRealtime

    override val authBuildable = AuthBuilder(
        object : AuthDependency {
            override val authUseCase by lazy { AuthUseCase(api, deriver, separator) }
            override val workDispatcher = this@AppRootDependency.workDispatcher
        },
    )

    override val unlockBuildable = UnlockBuilder(
        object : UnlockDependency {
            override val unlocker = Unlocker { password, session -> sessions.unlock(password, session) }
            override val workDispatcher = this@AppRootDependency.workDispatcher
        },
    )

    private val vaultListBuildable = VaultListBuilder(
        object : VaultListDependency {
            override val clipboard = this@AppRootDependency.clipboard
            override val workDispatcher = this@AppRootDependency.workDispatcher
            override val clock: () -> Instant = Instant::now
        },
    )

    override val mainBuildable = MainBuilder(
        object : MainDependency {
            override val vaultListBuildable = this@AppRootDependency.vaultListBuildable
            override val generatorBuildable = GeneratorBuilder(
                object : GeneratorDependency {
                    override val clipboard = this@AppRootDependency.clipboard
                    override val generator = PasswordGenerator()
                },
            )
            override val settingsBuildable = SettingsBuilder(
                object : SettingsDependency {
                    override val autoLockStore = this@AppRootDependency.autoLockStore
                    override val pendingChanges = PendingChanges { sessions.hasPendingChanges() }
                    override val documents = ContentResolverDocuments(context.contentResolver)
                    override val workDispatcher = this@AppRootDependency.workDispatcher
                    override val clock: () -> Instant = Instant::now
                    override val today: () -> LocalDate = LocalDate::now
                },
            )
        },
    )

    override val sessionGateway = object : VaultSessionGateway {
        override fun lockedAccountEmail() = sessions.lockedAccount()?.email
        override fun openAfterAuth(result: AuthResult) = sessions.openAfterAuth(result)
        override fun logout(session: Session?) = sessions.logout(session)
        override fun save(opened: OpenedVault, vault: VaultData) = sessions.save(opened, vault)
        override fun exportBackup(opened: OpenedVault, vault: VaultData, now: Instant) = backupSession.export(opened, vault, now)
        override fun restoreBackup(opened: OpenedVault, current: VaultData, bytes: ByteArray, password: String, now: Instant) =
            backupSession.restore(opened, current, bytes, password, now)
        override fun changePassword(opened: OpenedVault, vault: VaultData, current: String, new: String, confirmation: String) =
            accounts.changePassword(opened, vault, current, new, confirmation)
        override fun deleteAccount(opened: OpenedVault, password: String) = accounts.deleteAccount(opened, password)
    }
}
