import XCTest
import SwiftUI
import MicroRIBs
@testable import KeyManageUIAdapter

final class KeyManageUIAdapterTests: XCTestCase {

	@MainActor
	func testSwiftUIViewControllable_init_withRootView() {
		let view = Text("Test")
		let controllable = SwiftUIViewControllable(rootView: view)

		#if os(macOS)
		XCTAssertTrue(controllable.nsViewController is NSHostingController<Text>)
		#elseif os(iOS) || os(tvOS) || os(visionOS)
		XCTAssertTrue(controllable.uiViewController is UIHostingController<Text>)
		#endif
	}

	@MainActor
	func testSwiftUIViewControllable_init_withHostingController() {
		let view = Text("Custom")
		#if os(macOS)
		let hostingController = NSHostingController(rootView: view)
		let controllable = SwiftUIViewControllable(hostingController: hostingController)
		XCTAssertIdentical(controllable.nsViewController, hostingController)
		#elseif os(iOS) || os(tvOS) || os(visionOS)
		let hostingController = UIHostingController(rootView: view)
		let controllable = SwiftUIViewControllable(hostingController: hostingController)
		XCTAssertIdentical(controllable.uiViewController, hostingController)
		#endif
	}
}
