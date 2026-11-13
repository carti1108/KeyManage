package com.keymanage.feature.vaultlist

import com.carti.ribs.Builder
import com.carti.ribs.Component
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorViewFactory

class VaultListComponent(dependency: VaultListDependency) :
    Component<VaultListDependency>(dependency), PasswordGeneratorDependency {
    override val passwordGeneratorViewFactory: PasswordGeneratorViewFactory get() = dependency.passwordGeneratorViewFactory
    override val clipboard: ClipboardPort get() = dependency.clipboard
    override val randomBytes: RandomBytes get() = dependency.randomBytes
}

class VaultListBuilder(dependency: VaultListDependency) : Builder<VaultListDependency>(dependency) {
    fun build(listener: VaultListListener): VaultListRouter {
        val component = VaultListComponent(dependency)
        val view = dependency.vaultListViewFactory.makeVaultListView()
        val interactor = VaultListInteractor(
            presenter = view,
            session = dependency.session,
            clipboard = dependency.clipboard,
            clock = dependency.clock,
            listener = listener,
        )
        return VaultListRouter(interactor, view, PasswordGeneratorBuilder(component))
    }
}
