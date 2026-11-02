import Foundation
import KeyManageDomain

#if os(macOS)
import AppKit

public final class SystemPasteboard: PasteboardProtocol, @unchecked Sendable {
	public init() {}

	public func setString(_ string: String) throws {
		let pb = NSPasteboard.general
		pb.clearContents()
		guard pb.setString(string, forType: .string) else {
			throw ClipboardError.copyFailed
		}
	}

	public func clear() throws {
		let pb = NSPasteboard.general
		pb.clearContents()
	}

	public func getString() -> String? {
		NSPasteboard.general.string(forType: .string)
	}
}
#elseif os(iOS)
import UIKit

public final class SystemPasteboard: PasteboardProtocol, @unchecked Sendable {
	public init() {}

	public func setString(_ string: String) throws {
		UIPasteboard.general.string = string
	}

	public func clear() throws {
		UIPasteboard.general.items = []
	}

	public func getString() -> String? {
		UIPasteboard.general.string
	}
}
#endif
