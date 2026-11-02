import Foundation
import Testing
import MicroRIBs
import KeyManageDomain
import PasswordGeneratorFeatureInterface
@testable import PasswordGeneratorFeatureImplementation
import PasswordGeneratorFeatureTesting

#if os(macOS)
import AppKit
#elseif os(iOS)
import UIKit
#endif

// MARK: - Test Doubles

@MainActor
final class PasswordGeneratorPresenterSpy: PasswordGeneratorPresentable {
	weak var listener: PasswordGeneratorPresentableListener?
	var displayGeneratedPasswordCallCount = 0
	var receivedPassword: String?
	var receivedScore: Int?
	var receivedLevel: PasswordSecurityLevel?
	var receivedNotice: String?
	var showToastCallCount = 0
	var receivedToastMessage: String?

	func displayGeneratedPassword(_ password: String, score: Int, level: PasswordSecurityLevel, notice: String?) {
		displayGeneratedPasswordCallCount += 1
		receivedPassword = password
		receivedScore = score
		receivedLevel = level
		receivedNotice = notice
	}

	func showToast(message: String) {
		showToastCallCount += 1
		receivedToastMessage = message
	}
}

@MainActor
final class PasswordGeneratorViewControllableSpy: PasswordGeneratorViewControllable {
#if os(macOS)
	var nsViewController: NSViewController = NSViewController()
#elseif os(iOS)
	var uiViewController: UIViewController = UIViewController()
#endif
}

@MainActor
final class MockPasswordGeneratorInteractable: PasswordGeneratorInteractable {
	weak var router: PasswordGeneratorRouting?
	weak var listener: PasswordGeneratorListener?

	var isActive: Bool {
		isActiveSubject.value
	}
	var isActiveStream: AsyncStream<Bool> {
		isActiveSubject.stream
	}
	private let isActiveSubject = CurrentValueAsyncStream<Bool>(false)

	func activate() {
		isActiveSubject.send(true)
	}

	func deactivate() {
		isActiveSubject.send(false)
	}
}

final class MockClipboardManager: ClipboardManagerProtocol, @unchecked Sendable {
	var autoBurnInterval: TimeInterval = 30.0
	var isTimerActive: Bool = false
	var copiedTexts: [String] = []
	var copyCallCount = 0
	var handleLockCallCount = 0
	var clearCallCount = 0
	var shouldThrowCopy: Error? = nil

	init() {}

	func copy(_ text: String) throws {
		copyCallCount += 1
		if let error = shouldThrowCopy {
			throw error
		}
		copiedTexts.append(text)
		isTimerActive = true
	}

	func handleLock() {
		handleLockCallCount += 1
		clear()
	}

	func clear() {
		clearCallCount += 1
		copiedTexts.removeAll()
		isTimerActive = false
	}
}

final class MockPasswordGenerator: PasswordGeneratorProtocol, @unchecked Sendable {
	var generateCallCount = 0
	var receivedOptions: PasswordGenerationOptions?
	var passwordToReturn: String = "MockP@ssw0rd123!"
	var shouldThrowError: Error? = nil

	init(passwordToReturn: String = "MockP@ssw0rd123!") {
		self.passwordToReturn = passwordToReturn
	}

	func generate(options: PasswordGenerationOptions) throws -> String {
		generateCallCount += 1
		receivedOptions = options
		if let error = shouldThrowError {
			throw error
		}
		return passwordToReturn
	}
}

@MainActor
final class PasswordGeneratorDependencyMock: PasswordGeneratorDependency {
	let clipboardManager: ClipboardManagerProtocol

	init(clipboardManager: ClipboardManagerProtocol = MockClipboardManager()) {
		self.clipboardManager = clipboardManager
	}
}

// MARK: - PasswordGeneratorTests Suite

@Suite("PasswordGeneratorFeature microRIBs 단위 테스트")
@MainActor
struct PasswordGeneratorTests {

	// MARK: 1. 기본 비밀번호 자동 생성 (GEN-001)

	@Test("didBecomeActive 호출 시 기본 16자 비밀번호가 자동 생성되어 Presenter에 전달되는지 검증")
	func didBecomeActive_generatesDefaultPassword_andDisplaysOnPresenter() {
		let clipboard = MockClipboardManager()
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			presenter: presenter
		)

		interactor.didBecomeActive()

		#expect(presenter.displayGeneratedPasswordCallCount == 1)
		#expect(presenter.receivedPassword?.count == 16)
		#expect(presenter.receivedScore != nil && presenter.receivedScore! > 0)
		#expect(presenter.receivedLevel == .secure || presenter.receivedLevel == .warning)
	}

	// MARK: 2. 옵션 변경 및 재생성 (GEN-001)

	@Test("didChangeOptions 호출 시 변경된 옵션으로 비밀번호가 생성되고 점수/보안 등급이 Presenter에 전달되는지 검증")
	func didChangeOptions_withValidOptions_updatesPasswordAndDisplaysEntropy() {
		let clipboard = MockClipboardManager()
		let generator = MockPasswordGenerator(passwordToReturn: "Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!")
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			generator: generator,
			presenter: presenter
		)

		let options = PasswordGenerationOptions(
			length: 24,
			includeUppercase: true,
			includeLowercase: true,
			includeDigits: true,
			includeSymbols: true
		)
		interactor.didChangeOptions(options)

		#expect(generator.generateCallCount == 1)
		#expect(generator.receivedOptions?.length == 24)
		#expect(presenter.displayGeneratedPasswordCallCount == 1)
		#expect(presenter.receivedPassword == "Aa1!Aa1!Aa1!Aa1!Aa1!Aa1!")
		#expect(presenter.receivedScore != nil && presenter.receivedScore! >= 80)
		#expect(presenter.receivedLevel == .secure)
	}

	@Test("didRequestRegenerate 호출 시 새 비밀번호가 생성되어 Presenter에 전달되는지 검증")
	func didRequestRegenerate_generatesNewPassword_andDisplaysOnPresenter() {
		let clipboard = MockClipboardManager()
		let generator = MockPasswordGenerator(passwordToReturn: "RegeneratedPass!1")
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			generator: generator,
			presenter: presenter
		)

		interactor.didRequestRegenerate()

		#expect(generator.generateCallCount == 1)
		#expect(presenter.displayGeneratedPasswordCallCount == 1)
		#expect(presenter.receivedPassword == "RegeneratedPass!1")
	}

	@Test("모든 문자셋 미선택 등 유효하지 않은 옵션 시 에러 토스트 메시지가 전달되는지 검증")
	func didChangeOptions_withNoCharacterSetSelected_showsErrorToast() {
		let clipboard = MockClipboardManager()
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			generator: PasswordGenerator(),
			presenter: presenter
		)

		let invalidOptions = PasswordGenerationOptions(
			length: 16,
			includeUppercase: false,
			includeLowercase: false,
			includeDigits: false,
			includeSymbols: false
		)
		interactor.didChangeOptions(invalidOptions)

		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == PasswordGeneratorError.noCharacterSetSelected.localizedDescription)
		#expect(presenter.displayGeneratedPasswordCallCount == 1)
		#expect(presenter.receivedPassword == "")
		#expect(presenter.receivedScore == 0)
		#expect(presenter.receivedLevel == .danger)
	}

	@Test("최소 길이 미달 등 유효하지 않은 길이 옵션 시 에러 토스트 메시지가 전달되는지 검증")
	func didChangeOptions_withInvalidLength_showsErrorToast() {
		let clipboard = MockClipboardManager()
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			generator: PasswordGenerator(),
			presenter: presenter
		)

		let invalidOptions = PasswordGenerationOptions(length: 4)
		interactor.didChangeOptions(invalidOptions)

		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == PasswordGeneratorError.invalidLength.localizedDescription)
		#expect(presenter.displayGeneratedPasswordCallCount == 1)
		#expect(presenter.receivedPassword == "")
		#expect(presenter.receivedScore == 0)
		#expect(presenter.receivedLevel == .danger)
	}

	// MARK: 3. 클립보드 복사 및 자동 소각 연동 (COM-001)

	@Test("didRequestCopyPassword 호출 시 clipboardManager.copy가 1회 호출되고 성공 안내 토스트가 발송되는지 검증")
	func didRequestCopyPassword_withValidPassword_copiesToClipboardAndShowsAutoBurnToast() {
		let clipboard = MockClipboardManager()
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			presenter: presenter
		)

		interactor.didRequestCopyPassword("Secret123!")

		#expect(clipboard.copyCallCount == 1)
		#expect(clipboard.copiedTexts == ["Secret123!"])
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == ClipboardStrings.autoBurnNoticeMessage)
	}

	@Test("클립보드 복사 실패 시 에러 토스트 메시지가 발송되는지 검증")
	func didRequestCopyPassword_whenClipboardThrowsError_showsCopyFailedToast() {
		let clipboard = MockClipboardManager()
		clipboard.shouldThrowCopy = ClipboardError.copyFailed
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			presenter: presenter
		)

		interactor.didRequestCopyPassword("Secret123!")

		#expect(clipboard.copyCallCount == 1)
		#expect(presenter.showToastCallCount == 1)
		#expect(presenter.receivedToastMessage == ClipboardStrings.copyFailedMessage)
	}

	@Test("빈 문자열 복사 요청 시 클립보드 복사를 수행하지 않는지 방어 검증")
	func didRequestCopyPassword_withEmptyPassword_doesNotCopyToClipboard() {
		let clipboard = MockClipboardManager()
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			presenter: presenter
		)

		interactor.didRequestCopyPassword("")

		#expect(clipboard.copyCallCount == 0)
		#expect(presenter.showToastCallCount == 0)
	}

	// MARK: 4. Router & Builder 바인딩

	@Test("PasswordGeneratorRouter 초기화 시 interactor.router 바인딩 검증")
	func router_bindsInteractorRouterSelf() {
		let interactor = MockPasswordGeneratorInteractable()
		let viewController = PasswordGeneratorViewControllableSpy()

		let router = PasswordGeneratorRouter(
			interactor: interactor,
			viewController: viewController
		)

		#expect(interactor.router === router)
	}

	@Test("PasswordGeneratorBuilder.build 호출 시 Routing, ViewControllable, Interactor 정상 조립 및 Listener 바인딩 검증")
	func builder_buildsRoutingAndAssemblesComponents() {
		let dependency = PasswordGeneratorDependencyMock()
		let builder = PasswordGeneratorBuilder(dependency: dependency)
		let listener = MockPasswordGeneratorListener()

		let routing = builder.build(withListener: listener)

		#expect(routing.viewControllable is PasswordGeneratorViewController)
		#expect(routing.interactable is PasswordGeneratorInteractor)

		let interactor = routing.interactable as? PasswordGeneratorInteractor
		#expect(interactor?.listener === listener)
	}

	// MARK: 5. ViewModel 단위 테스트

	@Test("ViewModel: updateLength 호출 시 길이 상태가 갱신되고 onOptionsChanged 클로저가 트리거되는지 검증")
	func viewModel_updateLength_updatesLengthAndTriggersCallback() {
		let vm = PasswordGeneratorViewModel()
		var capturedOptions: PasswordGenerationOptions?
		vm.onOptionsChanged = { capturedOptions = $0 }

		vm.updateLength(20.0)

		#expect(vm.length == 20.0)
		#expect(capturedOptions?.length == 20)
	}

	@Test("ViewModel: toggle 메서드 호출 시 각 옵션의 불리언 상태가 토글되고 onOptionsChanged가 트리거되는지 검증")
	func viewModel_toggleOptions_togglesStateAndTriggersCallback() {
		let vm = PasswordGeneratorViewModel()
		var callbackCount = 0
		vm.onOptionsChanged = { _ in callbackCount += 1 }

		vm.toggleUppercase()
		vm.toggleLowercase()
		vm.toggleDigits()
		vm.toggleSymbols()

		#expect(vm.includeUppercase == false)
		#expect(vm.includeLowercase == false)
		#expect(vm.includeDigits == false)
		#expect(vm.includeSymbols == false)
		#expect(callbackCount == 4)
	}

	@Test("ViewModel: regenerate 및 copyPassword 호출 시 관련 클로저가 현재 비밀번호와 함께 트리거되는지 검증")
	func viewModel_regenerateAndCopy_triggersCallbacks() {
		let vm = PasswordGeneratorViewModel()
		var regenerateCalled = false
		var copiedPassword: String?
		vm.onRequestRegenerate = { regenerateCalled = true }
		vm.onRequestCopy = { copiedPassword = $0 }
		vm.generatedPassword = "MyGeneratedPassword"

		vm.regenerate()
		vm.copyPassword()

		#expect(regenerateCalled == true)
		#expect(copiedPassword == "MyGeneratedPassword")
	}

	@Test("ViewModel: showToast 및 clearToast 호출 시 toastMessage 상태 변화 검증")
	func viewModel_showToastAndClearToast_updatesToastMessage() {
		let vm = PasswordGeneratorViewModel()

		vm.showToast("토스트 테스트 메시지")
		#expect(vm.toastMessage == "토스트 테스트 메시지")

		vm.clearToast()
		#expect(vm.toastMessage == nil)
	}

	// MARK: 6. GEN-001 취약점 진단 및 연속 숫자 알림 연동

	@Test("연속 숫자가 포함된 비밀번호 생성 시 SequentialDigitsChecker.noticeMessage가 Presenter의 notice로 전달되는지 검증")
	func interactor_whenPasswordHasSequentialDigits_deliversNoticeMessage() {
		let clipboard = MockClipboardManager()
		let generator = MockPasswordGenerator(passwordToReturn: "Abc123!XYZ")
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			generator: generator,
			presenter: presenter
		)

		interactor.didRequestRegenerate()

		#expect(presenter.receivedPassword == "Abc123!XYZ")
		#expect(presenter.receivedNotice == SequentialDigitsChecker.noticeMessage)
	}

	@Test("연속 숫자가 없는 비밀번호 생성 시 notice가 nil로 전달되는지 검증")
	func interactor_whenPasswordHasNoSequentialDigits_deliversNilNotice() {
		let clipboard = MockClipboardManager()
		let generator = MockPasswordGenerator(passwordToReturn: "Ab1!Cd2@Ef3#")
		let presenter = PasswordGeneratorPresenterSpy()
		let interactor = PasswordGeneratorInteractor(
			clipboardManager: clipboard,
			generator: generator,
			presenter: presenter
		)

		interactor.didRequestRegenerate()

		#expect(presenter.receivedPassword == "Ab1!Cd2@Ef3#")
		#expect(presenter.receivedNotice == nil)
	}

	@Test("ViewController가 Presenter 프로토콜 구현을 통해 ViewModel 상태를 정확히 반영하는지 검증")
	func viewController_presenterMethods_updateViewModelState() {
		let vc = PasswordGeneratorViewController()

		vc.displayGeneratedPassword("NewPass1!", score: 85, level: .secure, notice: "NoticeMessage")

		#expect(vc.viewModel.generatedPassword == "NewPass1!")
		#expect(vc.viewModel.score == 85)
		#expect(vc.viewModel.securityLevel == .secure)
		#expect(vc.viewModel.sequentialDigitsNotice == "NoticeMessage")

		vc.showToast(message: "VC Toast Message")
		#expect(vc.viewModel.toastMessage == "VC Toast Message")
	}
}
