import Foundation
import XCTest
@testable import KeyManageData

final class KeyManageDataTests: XCTestCase {
	func testDataModuleName() {
		XCTAssertEqual(KeyManageDataModule.name, "KeyManageData")
	}
}
