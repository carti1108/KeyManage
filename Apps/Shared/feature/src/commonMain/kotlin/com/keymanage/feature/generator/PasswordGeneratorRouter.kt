package com.keymanage.feature.generator

import com.carti.ribs.ViewableRouter

class PasswordGeneratorRouter(interactor: PasswordGeneratorInteractor, view: PasswordGeneratorViewControllable) :
    ViewableRouter<PasswordGeneratorInteractor, PasswordGeneratorViewControllable>(interactor, view)
