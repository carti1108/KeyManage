package com.keymanage.androidsession

import android.util.Log
import androidx.fragment.app.testing.EmptyFragmentActivity
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keymanage.domain.lock.BiometricError
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 볼트 키를 Keystore 생체 전용 키로 감싸 두는 저장소 (ADR-AND-002).
// 지문 일치 신호(adb emu finger touch)는 실행하는 쪽이 보낸다. logcat의 "KM_BIOMETRIC_PROMPT"가 신호를 보낼 시점이다
@RunWith(AndroidJUnit4::class)
class KeystoreBiometricKeyStoreTest {
    private val service = "key-store-test"

    private fun withStore(block: suspend (KeystoreBiometricKeyStore) -> Unit) {
        launchActivity<EmptyFragmentActivity>().use { scenario ->
            lateinit var activity: EmptyFragmentActivity
            scenario.onActivity { activity = it }
            val store = KeystoreBiometricKeyStore(activity.applicationContext, service) { activity }
            try {
                runBlocking { block(store) }
            } finally {
                store.clear()
            }
        }
    }

    @Test
    fun hasNoKeyUntilOneIsSealed() = withStore { store ->
        assertFalse(store.hasKey())
    }

    @Test
    fun openingWithoutAKeyIsInvalidatedWithoutAPrompt() = withStore { store ->
        assertFailsWith<BiometricError.Invalidated> { store.open() }
    }

    @Test
    fun clearingWithoutAKeyDoesNothing() = withStore { store ->
        store.clear()

        assertFalse(store.hasKey())
    }

    @Test
    fun withoutAnActivityThePromptCannotBeShown() {
        launchActivity<EmptyFragmentActivity>().use { scenario ->
            lateinit var activity: EmptyFragmentActivity
            scenario.onActivity { activity = it }
            val store = KeystoreBiometricKeyStore(activity.applicationContext, service) { null }

            assertFailsWith<BiometricError.Unavailable> { runBlocking { store.seal(ByteArray(32)) } }
            assertFalse(store.hasKey())
        }
    }

    @Test
    fun sealedKeyComesBackAfterFingerprintMatchAndIsGoneAfterClear() = withStore { store ->
        val key = ByteArray(32) { (it * 5).toByte() }
        assertTrue(store.isAvailable(), "에뮬레이터에 화면 잠금과 지문을 먼저 등록한다")

        Log.i("KMTEST", "KM_BIOMETRIC_PROMPT seal")
        store.seal(key)
        assertTrue(store.hasKey())

        Log.i("KMTEST", "KM_BIOMETRIC_PROMPT open")
        assertContentEquals(key, store.open())

        store.clear()
        assertFalse(store.hasKey())
        assertFailsWith<BiometricError.Invalidated> { store.open() }
    }
}
