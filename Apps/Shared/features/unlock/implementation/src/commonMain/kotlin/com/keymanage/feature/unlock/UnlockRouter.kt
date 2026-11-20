package com.keymanage.feature.unlock

import com.carti.ribs.ViewableRouter

class UnlockRouter(interactor: UnlockInteractor, view: UnlockViewControllable) :
    ViewableRouter<UnlockInteractor, UnlockViewControllable>(interactor, view), UnlockRouting {
    override val isFinishingSession: Boolean get() = interactor.isFinishingSession
}
