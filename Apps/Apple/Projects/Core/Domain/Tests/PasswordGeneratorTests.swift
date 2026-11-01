import Foundation
import XCTest
@testable import KeyManageDomain

final class PasswordGeneratorTests: XCTestCase {

	// MARK: - 1. PasswordGenerationOptions 테스트

	func testDefaultGenerationOptions() {
		let options = PasswordGenerationOptions()
		XCTAssertEqual(options.length, 16)
		XCTAssertTrue(options.includeUppercase)
		XCTAssertTrue(options.includeLowercase)
		XCTAssertTrue(options.includeDigits)
		XCTAssertTrue(options.includeSymbols)
	}

	func testGenerationOptionsConstants() {
		XCTAssertEqual(PasswordGenerationOptions.minimumLength, 8)
		XCTAssertEqual(PasswordGenerationOptions.maximumLength, 64)
		XCTAssertEqual(PasswordGenerationOptions.defaultLength, 16)

		XCTAssertEqual(PasswordGenerationOptions.uppercaseCharacterSet, "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
		XCTAssertEqual(PasswordGenerationOptions.uppercaseCharacterSet.count, 26)

		XCTAssertEqual(PasswordGenerationOptions.lowercaseCharacterSet, "abcdefghijklmnopqrstuvwxyz")
		XCTAssertEqual(PasswordGenerationOptions.lowercaseCharacterSet.count, 26)

		XCTAssertEqual(PasswordGenerationOptions.digitsCharacterSet, "0123456789")
		XCTAssertEqual(PasswordGenerationOptions.digitsCharacterSet.count, 10)

		XCTAssertEqual(PasswordGenerationOptions.symbolsCharacterSet, "!@#$%^&*()_+-=[]{}|;:,.<>?")
		XCTAssertEqual(PasswordGenerationOptions.symbolsCharacterSet.count, 26)
	}

	func testCustomGenerationOptions() {
		let options = PasswordGenerationOptions(
			length: 24,
			includeUppercase: true,
			includeLowercase: false,
			includeDigits: true,
			includeSymbols: false
		)
		XCTAssertEqual(options.length, 24)
		XCTAssertTrue(options.includeUppercase)
		XCTAssertFalse(options.includeLowercase)
		XCTAssertTrue(options.includeDigits)
		XCTAssertFalse(options.includeSymbols)
	}

	// MARK: - 2. PasswordGeneratorError 테스트

	func testGeneratorErrorDescriptions() {
		let invalidLengthError = PasswordGeneratorError.invalidLength
		XCTAssertEqual(
			invalidLengthError.errorDescription,
			"비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다."
		)

		let noCharsetError = PasswordGeneratorError.noCharacterSetSelected
		XCTAssertEqual(
			noCharsetError.errorDescription,
			"최소 1개 이상의 문자 종류를 선택해야 합니다."
		)
	}

	func testGeneratorErrorEquatable() {
		XCTAssertEqual(PasswordGeneratorError.invalidLength, PasswordGeneratorError.invalidLength)
		XCTAssertEqual(PasswordGeneratorError.noCharacterSetSelected, PasswordGeneratorError.noCharacterSetSelected)
		XCTAssertNotEqual(PasswordGeneratorError.invalidLength, PasswordGeneratorError.noCharacterSetSelected)
	}

	// MARK: - 3. PasswordGenerator 유효성 검사 및 생성 규칙 테스트

	func testGenerateWithInvalidLengthBelowMinimumThrowsError() {
		let options7 = PasswordGenerationOptions(length: 7)
		XCTAssertThrowsError(try PasswordGenerator.generate(options: options7)) { error in
			XCTAssertEqual(error as? PasswordGeneratorError, .invalidLength)
		}

		let options0 = PasswordGenerationOptions(length: 0)
		XCTAssertThrowsError(try PasswordGenerator.generate(options: options0)) { error in
			XCTAssertEqual(error as? PasswordGeneratorError, .invalidLength)
		}

		let optionsNegative = PasswordGenerationOptions(length: -5)
		XCTAssertThrowsError(try PasswordGenerator.generate(options: optionsNegative)) { error in
			XCTAssertEqual(error as? PasswordGeneratorError, .invalidLength)
		}
	}

	func testGenerateWithInvalidLengthAboveMaximumThrowsError() {
		let options65 = PasswordGenerationOptions(length: 65)
		XCTAssertThrowsError(try PasswordGenerator.generate(options: options65)) { error in
			XCTAssertEqual(error as? PasswordGeneratorError, .invalidLength)
		}

		let options100 = PasswordGenerationOptions(length: 100)
		XCTAssertThrowsError(try PasswordGenerator.generate(options: options100)) { error in
			XCTAssertEqual(error as? PasswordGeneratorError, .invalidLength)
		}
	}

	func testGenerateWithNoCharacterSetSelectedThrowsError() {
		let options = PasswordGenerationOptions(
			length: 16,
			includeUppercase: false,
			includeLowercase: false,
			includeDigits: false,
			includeSymbols: false
		)
		XCTAssertThrowsError(try PasswordGenerator.generate(options: options)) { error in
			XCTAssertEqual(error as? PasswordGeneratorError, .noCharacterSetSelected)
		}
	}

	func testGenerateBoundaryLength8Succeeds() throws {
		let options8 = PasswordGenerationOptions(length: 8)
		let password = try PasswordGenerator.generate(options: options8)
		XCTAssertEqual(password.count, 8)
	}

	func testGenerateBoundaryLength64Succeeds() throws {
		let options64 = PasswordGenerationOptions(length: 64)
		let password = try PasswordGenerator.generate(options: options64)
		XCTAssertEqual(password.count, 64)
	}

	func testGenerateDefaultOptionsIncludesAllSelectedSets() throws {
		let generator = PasswordGenerator()
		let password = try generator.generate()

		XCTAssertEqual(password.count, 16)

		let upperSet = Set(PasswordGenerationOptions.uppercaseCharacterSet)
		let lowerSet = Set(PasswordGenerationOptions.lowercaseCharacterSet)
		let digitSet = Set(PasswordGenerationOptions.digitsCharacterSet)
		let symbolSet = Set(PasswordGenerationOptions.symbolsCharacterSet)

		XCTAssertTrue(password.contains(where: { upperSet.contains($0) }), "대문자가 최소 1자 이상 포함되어야 합니다.")
		XCTAssertTrue(password.contains(where: { lowerSet.contains($0) }), "소문자가 최소 1자 이상 포함되어야 합니다.")
		XCTAssertTrue(password.contains(where: { digitSet.contains($0) }), "숫자가 최소 1자 이상 포함되어야 합니다.")
		XCTAssertTrue(password.contains(where: { symbolSet.contains($0) }), "특수기호가 최소 1자 이상 포함되어야 합니다.")
	}

	func testGenerateRejectionSampling1000Iterations() throws {
		// GEN-001: 4개 문자군 100% 포함 여부 1,000회 반복 검증
		let options = PasswordGenerationOptions(length: 16)
		let upperSet = Set(PasswordGenerationOptions.uppercaseCharacterSet)
		let lowerSet = Set(PasswordGenerationOptions.lowercaseCharacterSet)
		let digitSet = Set(PasswordGenerationOptions.digitsCharacterSet)
		let symbolSet = Set(PasswordGenerationOptions.symbolsCharacterSet)

		for _ in 0..<1000 {
			let password = try PasswordGenerator.generate(options: options)
			XCTAssertEqual(password.count, 16)
			XCTAssertTrue(password.contains(where: { upperSet.contains($0) }))
			XCTAssertTrue(password.contains(where: { lowerSet.contains($0) }))
			XCTAssertTrue(password.contains(where: { digitSet.contains($0) }))
			XCTAssertTrue(password.contains(where: { symbolSet.contains($0) }))
		}
	}

	func testGenerateSingleCharacterSetOnly() throws {
		let options = PasswordGenerationOptions(
			length: 12,
			includeUppercase: true,
			includeLowercase: false,
			includeDigits: false,
			includeSymbols: false
		)
		let password = try PasswordGenerator.generate(options: options)
		XCTAssertEqual(password.count, 12)
		let upperSet = Set(PasswordGenerationOptions.uppercaseCharacterSet)
		XCTAssertTrue(password.allSatisfy { upperSet.contains($0) })
	}

	func testGenerateNonDeterministicRandomness() throws {
		let p1 = try PasswordGenerator.generate()
		let p2 = try PasswordGenerator.generate()
		XCTAssertNotEqual(p1, p2, "CSPRNG 무작위 추출 비밀번호는 매회 달라야 합니다.")
	}

	// MARK: - 4. EntropyScorer 풀 크기 및 점수 산출 테스트

	func testPoolSizeCalculation() {
		// 빈 문자열은 0
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: ""), 0)

		// 대문자만 (26)
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "ABC"), 26)

		// 소문자만 (26)
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "abc"), 26)

		// 숫자만 (10)
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "123456"), 10)

		// 특수기호 26종만 (26)
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "!@#"), 26)

		// 4개 문자군 전부 포함 (26 + 26 + 10 + 26 = 88)
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "Aa1!"), 88)

		// 소문자 + 그 외 문자 '~' (26 + 7 = 33)
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "abc~~~~~"), 33)

		// 한글 등 그 외 문자만 포함 (7)
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "한글"), 7)

		// 여러 종류의 그 외 문자(한글, 공백, 26종 외 특수기호)가 섞여도 7은 한 번만 더함
		XCTAssertEqual(EntropyScorer.calculatePoolSize(for: "한글 ~ emoji"), 26 + 7)
	}

	func testPlatformCommonSamplesEntropyScore() {
		// GEN-001 플랫폼 공통 샘플 검증
		// 1. "123456" -> 19점, danger
		XCTAssertEqual(EntropyScorer.score(for: "123456"), 19)
		XCTAssertEqual(EntropyScorer.securityLevel(for: "123456"), .danger)

		// 2. "abc~~~~~" -> 40점, danger
		XCTAssertEqual(EntropyScorer.score(for: "abc~~~~~"), 40)
		XCTAssertEqual(EntropyScorer.securityLevel(for: "abc~~~~~"), .danger)

		// 3. 4개 문자군 12자 ("Aa1!Aa1!Aa1!") -> 77점, warning
		XCTAssertEqual(EntropyScorer.score(for: "Aa1!Aa1!Aa1!"), 77)
		XCTAssertEqual(EntropyScorer.securityLevel(for: "Aa1!Aa1!Aa1!"), .warning)

		// 4. 4개 문자군 16자 ("Aa1!Aa1!Aa1!Aa1!") -> 100점, secure
		XCTAssertEqual(EntropyScorer.score(for: "Aa1!Aa1!Aa1!Aa1!"), 100)
		XCTAssertEqual(EntropyScorer.securityLevel(for: "Aa1!Aa1!Aa1!Aa1!"), .secure)
	}

	func testEmptyPasswordEntropyScore() {
		XCTAssertEqual(EntropyScorer.score(for: ""), 0)
		XCTAssertEqual(EntropyScorer.securityLevel(for: ""), .danger)
	}

	func testUnicodeCodePointLengthEntropyScore() {
		// "가나다라" 4글자, 풀 크기 7: floor(4 * log2(7)) = floor(4 * 2.80735...) = 11점
		XCTAssertEqual(EntropyScorer.score(for: "가나다라"), 11)
	}

	func testScoreBoundariesAndSecurityLevels() {
		// 60점 미만: danger ("취약")
		XCTAssertEqual(EntropyScorer.securityLevel(for: 0), .danger)
		XCTAssertEqual(EntropyScorer.securityLevel(for: 59), .danger)
		XCTAssertEqual(PasswordSecurityLevel.danger.title, "취약")

		// 60점 이상 80점 미만: warning ("보통")
		XCTAssertEqual(EntropyScorer.securityLevel(for: 60), .warning)
		XCTAssertEqual(EntropyScorer.securityLevel(for: 79), .warning)
		XCTAssertEqual(PasswordSecurityLevel.warning.title, "보통")

		// 80점 이상: secure ("안전")
		XCTAssertEqual(EntropyScorer.securityLevel(for: 80), .secure)
		XCTAssertEqual(EntropyScorer.securityLevel(for: 100), .secure)
		XCTAssertEqual(PasswordSecurityLevel.secure.title, "안전")
	}

	func testWeakWarningConstantsAndThreshold() {
		XCTAssertEqual(EntropyScorer.warningTitle, "취약한 비밀번호 경고")
		XCTAssertEqual(
			EntropyScorer.warningMessage,
			"현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요."
		)

		XCTAssertTrue(EntropyScorer.isWeak(score: 59))
		XCTAssertFalse(EntropyScorer.isWeak(score: 60))
		XCTAssertFalse(EntropyScorer.isWeak(score: 80))

		XCTAssertTrue(EntropyScorer.isWeak(password: "123456"))
		XCTAssertFalse(EntropyScorer.isWeak(password: "Aa1!Aa1!Aa1!Aa1!"))
	}

	// MARK: - 5. SequentialDigitsChecker 연속 ASCII 숫자 진단기 테스트

	func testPlatformCommonSamplesSequentialDigits() {
		// GEN-001 샘플 검증:
		// "a123b" -> true
		XCTAssertTrue(SequentialDigitsChecker.hasSequentialDigits("a123b"))

		// "9876" -> true
		XCTAssertTrue(SequentialDigitsChecker.hasSequentialDigits("9876"))

		// "890" -> false (9->0 넘어감은 단절)
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits("890"))

		// "1a2b3" -> false (중간에 비숫자 삽입)
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits("1a2b3"))

		// "12" -> false (2자리는 감지 안 됨)
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits("12"))

		// "121" -> false (방향 전환)
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits("121"))
	}

	func testSequentialDigitsNoticeMessage() {
		XCTAssertEqual(
			SequentialDigitsChecker.noticeMessage,
			"123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다."
		)
	}

	func testAdditionalSequentialDigitsCases() {
		// 내림차순 4자리
		XCTAssertTrue(SequentialDigitsChecker.hasSequentialDigits("3210"))

		// 0->9 넘어감은 단절
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits("098"))

		// 같은 숫자 반복은 연속 숫자가 아님 (차이 0)
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits("111"))

		// 빈 문자열 및 문자만 있는 경우
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits(""))
		XCTAssertFalse(SequentialDigitsChecker.hasSequentialDigits("abcdef"))

		// 단어 중간에 포함된 경우
		XCTAssertTrue(SequentialDigitsChecker.hasSequentialDigits("pass1234word"))

		// 12321: 앞의 123으로 인해 true
		XCTAssertTrue(SequentialDigitsChecker.hasSequentialDigits("12321"))
	}
}
