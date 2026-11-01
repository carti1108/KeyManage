import Foundation
import Security

/// 무작위 비밀번호 생성 옵션 (GEN-001)
public struct PasswordGenerationOptions: Sendable, Equatable {
	public static let minimumLength: Int = 8
	public static let maximumLength: Int = 64
	public static let defaultLength: Int = 16

	public static let uppercaseCharacterSet: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
	public static let lowercaseCharacterSet: String = "abcdefghijklmnopqrstuvwxyz"
	public static let digitsCharacterSet: String = "0123456789"
	public static let symbolsCharacterSet: String = "!@#$%^&*()_+-=[]{}|;:,.<>?"

	public var length: Int
	public var includeUppercase: Bool
	public var includeLowercase: Bool
	public var includeDigits: Bool
	public var includeSymbols: Bool

	public init(
		length: Int = Self.defaultLength,
		includeUppercase: Bool = true,
		includeLowercase: Bool = true,
		includeDigits: Bool = true,
		includeSymbols: Bool = true
	) {
		self.length = length
		self.includeUppercase = includeUppercase
		self.includeLowercase = includeLowercase
		self.includeDigits = includeDigits
		self.includeSymbols = includeSymbols
	}
}

/// 비밀번호 생성기 에러 (GEN-001)
public enum PasswordGeneratorError: LocalizedError, Equatable, Sendable {
	case invalidLength
	case noCharacterSetSelected

	public var errorDescription: String? {
		switch self {
		case .invalidLength:
			return "비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다."
		case .noCharacterSetSelected:
			return "최소 1개 이상의 문자 종류를 선택해야 합니다."
		}
	}
}

/// 비밀번호 생성 인터페이스 (GEN-001)
public protocol PasswordGeneratorProtocol: Sendable {
	func generate(options: PasswordGenerationOptions) throws -> String
}

/// CSPRNG 기반 비밀번호 무작위 생성기 (GEN-001)
public struct PasswordGenerator: PasswordGeneratorProtocol, Sendable {
	public init() {}

	public func generate(options: PasswordGenerationOptions = PasswordGenerationOptions()) throws -> String {
		return try Self.generate(options: options)
	}

	public static func generate(options: PasswordGenerationOptions = PasswordGenerationOptions()) throws -> String {
		guard options.length >= PasswordGenerationOptions.minimumLength,
		      options.length <= PasswordGenerationOptions.maximumLength else {
			throw PasswordGeneratorError.invalidLength
		}

		var charsets: [String] = []
		if options.includeUppercase {
			charsets.append(PasswordGenerationOptions.uppercaseCharacterSet)
		}
		if options.includeLowercase {
			charsets.append(PasswordGenerationOptions.lowercaseCharacterSet)
		}
		if options.includeDigits {
			charsets.append(PasswordGenerationOptions.digitsCharacterSet)
		}
		if options.includeSymbols {
			charsets.append(PasswordGenerationOptions.symbolsCharacterSet)
		}

		guard !charsets.isEmpty else {
			throw PasswordGeneratorError.noCharacterSetSelected
		}

		let pool = Array(charsets.joined())
		let poolSize = pool.count
		let limit = 256 - (256 % poolSize)
		let charsetSets = charsets.map { Set($0) }

		var buffer = [UInt8](repeating: 0, count: 64)
		var bufferIndex = buffer.count

		func nextUniformIndex() -> Int {
			while true {
				if bufferIndex >= buffer.count {
					let status = SecRandomCopyBytes(kSecRandomDefault, buffer.count, &buffer)
					guard status == errSecSuccess else {
						continue
					}
					bufferIndex = 0
				}
				let byte = Int(buffer[bufferIndex])
				bufferIndex += 1
				if byte < limit {
					return byte % poolSize
				}
			}
		}

		while true {
			var generatedChars = [Character]()
			generatedChars.reserveCapacity(options.length)
			for _ in 0..<options.length {
				let idx = nextUniformIndex()
				generatedChars.append(pool[idx])
			}

			let allSetsIncluded = charsetSets.allSatisfy { set in
				generatedChars.contains { set.contains($0) }
			}

			if allSetsIncluded {
				return String(generatedChars)
			}
		}
	}
}

/// 비밀번호 보안 등급 (GEN-001)
public enum PasswordSecurityLevel: String, Sendable, Equatable, CaseIterable {
	case danger
	case warning
	case secure

	public var title: String {
		switch self {
		case .danger:
			return "취약"
		case .warning:
			return "보통"
		case .secure:
			return "안전"
		}
	}
}

/// 엔트로피 점수 산출기 (GEN-001)
public struct EntropyScorer: Sendable {
	public static let warningTitle = "취약한 비밀번호 경고"
	public static let warningMessage = "현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요."

	public static let weakThreshold: Int = 60
	public static let secureThreshold: Int = 80
	public static let maxScore: Int = 100

	private static let uppercaseSet = Set(PasswordGenerationOptions.uppercaseCharacterSet)
	private static let lowercaseSet = Set(PasswordGenerationOptions.lowercaseCharacterSet)
	private static let digitsSet = Set(PasswordGenerationOptions.digitsCharacterSet)
	private static let symbolsSet = Set(PasswordGenerationOptions.symbolsCharacterSet)

	public static func calculatePoolSize(for password: String) -> Int {
		guard !password.isEmpty else { return 0 }

		var poolSize = 0
		var hasUpper = false
		var hasLower = false
		var hasDigit = false
		var hasSymbol = false
		var hasOther = false

		for ch in password {
			if uppercaseSet.contains(ch) {
				hasUpper = true
			} else if lowercaseSet.contains(ch) {
				hasLower = true
			} else if digitsSet.contains(ch) {
				hasDigit = true
			} else if symbolsSet.contains(ch) {
				hasSymbol = true
			} else {
				hasOther = true
			}
		}

		if hasUpper { poolSize += 26 }
		if hasLower { poolSize += 26 }
		if hasDigit { poolSize += 10 }
		if hasSymbol { poolSize += 26 }
		if hasOther { poolSize += 7 }

		return poolSize
	}

	public static func score(for password: String) -> Int {
		let length = password.unicodeScalars.count
		guard length > 0 else { return 0 }

		let poolSize = calculatePoolSize(for: password)
		guard poolSize > 1 else { return 0 }

		let entropy = Double(length) * log2(Double(poolSize))
		let calculatedScore = Int(floor(entropy))
		return min(maxScore, max(0, calculatedScore))
	}

	public static func securityLevel(for score: Int) -> PasswordSecurityLevel {
		if score < weakThreshold {
			return .danger
		} else if score < secureThreshold {
			return .warning
		} else {
			return .secure
		}
	}

	public static func securityLevel(for password: String) -> PasswordSecurityLevel {
		return securityLevel(for: score(for: password))
	}

	public static func isWeak(score: Int) -> Bool {
		return score < weakThreshold
	}

	public static func isWeak(password: String) -> Bool {
		return isWeak(score: score(for: password))
	}
}

/// 연속 ASCII 숫자 진단기 (GEN-001)
public struct SequentialDigitsChecker: Sendable {
	public static let noticeMessage = "123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다."
	public static let minimumSequenceLength = 3

	public static func hasSequentialDigits(_ password: String) -> Bool {
		let characters = Array(password)
		guard characters.count >= minimumSequenceLength else { return false }

		let asciiDigits = Set("0123456789")

		var run = 1
		var step = 0

		for i in 1..<characters.count {
			let prevChar = characters[i - 1]
			let currChar = characters[i]

			if asciiDigits.contains(prevChar) && asciiDigits.contains(currChar) {
				guard let prevDigit = prevChar.wholeNumberValue,
				      let currDigit = currChar.wholeNumberValue else {
					run = 1
					step = 0
					continue
				}
				let diff = currDigit - prevDigit
				if diff == 1 || diff == -1 {
					run = (diff == step) ? (run + 1) : 2
					step = diff
					if run >= minimumSequenceLength {
						return true
					}
				} else {
					run = 1
					step = 0
				}
			} else {
				run = 1
				step = 0
			}
		}

		return false
	}
}
