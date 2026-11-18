package com.keymanage.android.feature.root.tests

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.feature.root.impl.RootInteractor
import com.keymanage.android.feature.root.impl.RootViewState
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.EmptyPresenter
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RootAuthTest {
    @Test
    fun `인증 결과를 받으면 계정을 표시하고 아직 쓰지 않는 키를 0으로 덮어쓴다`() {
        // Given
        val store = ViewStateStore(RootViewState())
        val interactor = RootInteractor(EmptyPresenter(), store, ViewActionStream())
        val key = ByteArray(32) { 7 }
        val result = AuthResult(
            Session("t", Instant.parse("2026-12-18T00:00:00Z")),
            key,
            DeviceAccount("dev@example.com", 600_000, "AAAAAAAAAAAAAAAAAAAAAA=="),
        )

        // When
        interactor.onAuthenticated(result)

        // Then
        assertEquals("dev@example.com", store.current.signedInEmail)
        assertContentEquals(ByteArray(32), key)
    }
}
