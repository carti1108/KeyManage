package com.keymanage.android

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import com.keymanage.android.data.crypto.AesGcmVaultCipher
import com.keymanage.android.data.crypto.HkdfKeySeparator
import com.keymanage.android.data.crypto.Pbkdf2MasterKeyDeriver
import com.keymanage.android.data.remote.HttpApiClient
import com.keymanage.android.data.sync.FileDeviceRecordStore
import com.keymanage.android.data.vault.JsonVaultSerializer
import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.auth.AuthUseCase
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.VaultSessionUseCase
import com.keymanage.android.domain.sync.VaultSyncCoordinator
import com.keymanage.android.feature.auth.AuthDependency
import com.keymanage.android.feature.auth.impl.AuthBuilder
import com.keymanage.android.feature.root.RootDependency
import com.keymanage.android.feature.root.VaultSessionGateway
import com.keymanage.android.feature.root.impl.RootBuilder
import com.keymanage.android.feature.unlock.UnlockDependency
import com.keymanage.android.feature.unlock.Unlocker
import com.keymanage.android.feature.unlock.impl.UnlockBuilder
import com.keymanage.android.feature.vaultlist.impl.VaultListBuilder
import com.uber.rib.core.RibActivity
import com.uber.rib.core.ViewRouter
import kotlinx.coroutines.Dispatchers
import java.io.File

/** 앱의 유일한 Activity. Root RIB 트리를 만들어 붙인다. */
class MainActivity : RibActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 앱 전환 화면·화면 녹화에 볼트가 비치지 않게 한다(SEC-003 규칙 3)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }

    override fun createRouter(parentViewGroup: ViewGroup): ViewRouter<*, *> =
        RootBuilder(AppRootDependency(noBackupFilesDir)).build(parentViewGroup)
}

/** 앱 조립. 포트 구현(:core:data)을 피처 의존성에 넘기는 곳은 여기뿐이다(ADR-AND-001). */
private class AppRootDependency(storageDir: File) : RootDependency {
    private val api by lazy { HttpApiClient(BuildConfig.SERVER_URL) }
    private val deriver = Pbkdf2MasterKeyDeriver()
    private val separator = HkdfKeySeparator()
    private val cipher = AesGcmVaultCipher()
    private val serializer = JsonVaultSerializer()
    private val store = FileDeviceRecordStore(File(storageDir, "device-record.json"))
    private val sessions by lazy {
        VaultSessionUseCase(api, api, deriver, separator, cipher, serializer, VaultSyncCoordinator(api, cipher, serializer, store))
    }

    override val workDispatcher = Dispatchers.Default

    override val authBuildable = AuthBuilder(
        object : AuthDependency {
            override val authUseCase by lazy { AuthUseCase(api, deriver, separator) }
            override val workDispatcher = Dispatchers.Default
        },
    )

    override val unlockBuildable = UnlockBuilder(
        object : UnlockDependency {
            override val unlocker = Unlocker { password, session -> sessions.unlock(password, session) }
            override val workDispatcher = Dispatchers.Default
        },
    )

    override val vaultListBuildable = VaultListBuilder()

    override val sessionGateway = object : VaultSessionGateway {
        override fun lockedAccountEmail() = sessions.lockedAccount()?.email
        override fun openAfterAuth(result: AuthResult) = sessions.openAfterAuth(result)
        override fun logout(session: Session?) = sessions.logout(session)
    }
}
