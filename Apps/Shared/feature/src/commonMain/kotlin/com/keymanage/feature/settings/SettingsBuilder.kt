package com.keymanage.feature.settings

import com.carti.ribs.Builder

class SettingsBuilder(dependency: SettingsDependency) : Builder<SettingsDependency>(dependency) {
    fun build(listener: SettingsListener): SettingsRouter {
        val view = dependency.settingsViewFactory.makeSettingsView()
        val interactor = SettingsInteractor(view, dependency.session, dependency.backupFiles, dependency.environment, listener)
        return SettingsRouter(interactor, view)
    }
}
