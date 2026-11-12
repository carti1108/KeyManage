package com.keymanage.securestore

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

// 시뮬레이터 호스트 앱에서만 의미가 있다. Face ID 등록과 일치 신호는 실행 스크립트가 notifyutil로 보낸다.
// "KM_BIOMETRIC_PROMPT" 출력이 일치 신호를 보낼 시점이다
class BiometricKeychainSimulatorTest {
    private val store = BiometricKeychainStore(service = "com.keymanage.securestore.biometric-test")
    private val account = "biometric-account"

    @AfterTest
    fun cleanUp() = store.delete(account)

    @Test
    fun protectedSecret_roundTripsAfterBiometricMatch() = runBlocking {
        assertEquals(BiometricAvailability.AVAILABLE, LocalBiometricAuthenticator().availability())
        val secret = ByteArray(32) { (it * 7).toByte() }
        store.saveProtected(account, secret)

        println("KM_BIOMETRIC_PROMPT")
        val loaded = store.loadProtected(account, "보호된 키 테스트")

        assertContentEquals(secret, loaded)
    }
}
