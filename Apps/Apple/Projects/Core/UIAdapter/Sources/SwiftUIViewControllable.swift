import SwiftUI
import MicroRIBs

#if os(iOS) || os(tvOS) || os(visionOS)
import UIKit

/// A `ViewControllable` wrapper around a SwiftUI `View` hosted inside a `UIHostingController`.
@MainActor
public final class SwiftUIViewControllable<Content: View>: ViewControllable {

	public let hostingController: UIHostingController<Content>

	public var uiViewController: UIViewController {
		hostingController
	}

	public init(rootView: Content) {
		self.hostingController = UIHostingController(rootView: rootView)
	}

	public init(hostingController: UIHostingController<Content>) {
		self.hostingController = hostingController
	}
}
#elseif os(macOS)
import AppKit

/// A `ViewControllable` wrapper around a SwiftUI `View` hosted inside an `NSHostingController`.
@MainActor
public final class SwiftUIViewControllable<Content: View>: ViewControllable {

	public let hostingController: NSHostingController<Content>

	public var nsViewController: NSViewController {
		hostingController
	}

	public init(rootView: Content) {
		self.hostingController = NSHostingController(rootView: rootView)
	}

	public init(hostingController: NSHostingController<Content>) {
		self.hostingController = hostingController
	}
}
#else
#error("SwiftUIViewControllable is not supported on this platform.")
#endif
