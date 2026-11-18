package com.keymanage.android.feature.root.tests

import com.keymanage.android.feature.root.impl.RootInteractor
import com.keymanage.android.feature.root.impl.RootViewAction
import com.keymanage.android.feature.root.impl.RootViewState
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.EmptyPresenter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootInteractorTest {
    @Test
    fun `ViewAppeared 동작을 받으면 hasAppeared가 true가 된다`() {
        // Given
        val store = ViewStateStore(RootViewState())
        val interactor = RootInteractor(EmptyPresenter(), store, ViewActionStream())
        assertFalse(store.current.hasAppeared)

        // When
        interactor.handle(RootViewAction.ViewAppeared)

        // Then
        assertTrue(store.current.hasAppeared)
    }
}
