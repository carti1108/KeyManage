import Foundation
import MicroRIBs
import KeyManageDomain

@MainActor
public protocol PasswordGeneratorDependency: Dependency {
	var clipboardManager: ClipboardManagerProtocol { get }
}
