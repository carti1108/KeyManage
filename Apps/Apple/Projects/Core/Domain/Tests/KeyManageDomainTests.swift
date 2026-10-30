import Foundation
import XCTest
@testable import KeyManageDomain

final class KeyManageDomainTests: XCTestCase {
	func testDomainModuleName() {
		XCTAssertEqual(KeyManageDomainModule.name, "KeyManageDomain")
	}
}
