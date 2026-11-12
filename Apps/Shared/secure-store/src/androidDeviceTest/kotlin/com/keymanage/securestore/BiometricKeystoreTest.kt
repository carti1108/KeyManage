package com.keymanage.securestore

import android.util.Log
import androidx.fragment.app.testing.EmptyFragmentActivity
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

// 지문 등록과 일치 신호(adb emu finger touch)는 실행 스크립트가 보낸다.
// logcat의 "KM_BIOMETRIC_PROMPT"가 신호를 보낼 시점이다(저장 1회, 조회 1회)
@RunWith(AndroidJUnit4::class)
class BiometricKeystoreTest {

    @Test
    fun protectedSecret_roundTripsAfterFingerprintMatch() {
        launchActivity<EmptyFragmentActivity>().use { scenario ->
            lateinit var activity: EmptyFragmentActivity
            scenario.onActivity { activity = it }
            val store = BiometricKeystoreStore(activity, "biometric-test")
            val secret = ByteArray(32) { (it * 7).toByte() }
            try {
                runBlocking {
                    assertEquals(BiometricAvailability.AVAILABLE, AndroidBiometricAuthenticator(activity).availability())
                    Log.i("KMTEST", "KM_BIOMETRIC_PROMPT save")
                    store.saveProtected("biometric-account", secret)
                    Log.i("KMTEST", "KM_BIOMETRIC_PROMPT load")
                    assertContentEquals(secret, store.loadProtected("biometric-account", "보호된 키 조회"))
                }
            } finally {
                store.delete("biometric-account")
                store.deleteKey()
            }
        }
    }
}
