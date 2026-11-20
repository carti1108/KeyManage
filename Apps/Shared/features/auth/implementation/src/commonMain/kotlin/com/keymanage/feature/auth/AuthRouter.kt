package com.keymanage.feature.auth

import com.carti.ribs.ViewableRouter

class AuthRouter(interactor: AuthInteractor, view: AuthViewControllable) :
    ViewableRouter<AuthInteractor, AuthViewControllable>(interactor, view), AuthRouting {
    override fun showNotice(notice: String) = interactor.showNotice(notice)
}
