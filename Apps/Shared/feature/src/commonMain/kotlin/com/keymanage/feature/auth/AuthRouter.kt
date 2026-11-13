package com.keymanage.feature.auth

import com.carti.ribs.ViewableRouter

class AuthRouter(interactor: AuthInteractor, view: AuthViewControllable) :
    ViewableRouter<AuthInteractor, AuthViewControllable>(interactor, view)
