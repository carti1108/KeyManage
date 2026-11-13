package com.keymanage.feature.settings

import com.carti.ribs.ViewableRouter

class SettingsRouter(interactor: SettingsInteractor, view: SettingsViewControllable) :
    ViewableRouter<SettingsInteractor, SettingsViewControllable>(interactor, view)
