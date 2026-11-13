package com.keymanage.feature.generator

import com.carti.ribs.Builder

class PasswordGeneratorBuilder(dependency: PasswordGeneratorDependency) : Builder<PasswordGeneratorDependency>(dependency) {
    /**
     * @param listener 항목 폼 안에서 쓸 때 넘긴다. 있으면 사용·닫기 버튼이 생긴다. 생성기 탭처럼 혼자 쓰면 null
     * @param allowCopy 복사 버튼을 둘지. 생성기 탭에서 true
     */
    fun build(listener: PasswordGeneratorListener?, allowCopy: Boolean): PasswordGeneratorRouter {
        val view = dependency.passwordGeneratorViewFactory.makePasswordGeneratorView()
        val interactor = PasswordGeneratorInteractor(
            presenter = view,
            clipboard = dependency.clipboard,
            random = randomSourceOf(dependency.randomBytes),
            listener = listener,
            allowCopy = allowCopy,
        )
        return PasswordGeneratorRouter(interactor, view)
    }
}
