package com.keymanage.feature.unlock

import com.carti.ribs.Builder

class UnlockBuilder(dependency: UnlockDependency) : Builder<UnlockDependency>(dependency) {
    fun build(listener: UnlockListener): UnlockRouter {
        val view = dependency.unlockViewFactory.makeUnlockView()
        val interactor = UnlockInteractor(view, dependency.session, listener)
        return UnlockRouter(interactor, view)
    }
}
