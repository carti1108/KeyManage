import Foundation
import MicroRIBs
import KeyManageDomain

@MainActor
public protocol VaultListDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var clipboardManager: ClipboardManagerProtocol { get }
}
