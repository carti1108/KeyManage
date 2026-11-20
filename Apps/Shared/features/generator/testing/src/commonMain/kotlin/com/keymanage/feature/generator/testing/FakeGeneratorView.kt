package com.keymanage.feature.generator.testing

import com.keymanage.feature.generator.PasswordGeneratorPresentableListener
import com.keymanage.feature.generator.PasswordGeneratorViewContract
import com.keymanage.feature.generator.PasswordGeneratorViewState

/** 그린 상태를 모두 기록하는 가짜 뷰 */
class FakeGeneratorView : PasswordGeneratorViewContract {
    override var listener: PasswordGeneratorPresentableListener? = null
    val states = mutableListOf<PasswordGeneratorViewState>()

    override fun render(state: PasswordGeneratorViewState) {
        states += state
    }
}
