import Foundation
import KeyManageDomain

struct VaultListViewState: Equatable, Sendable {
	var searchQuery: String = ""
	var items: [VaultItem] = []
	var toastMessage: String? = nil
	var revealedPasswordIds: Set<String> = []
	var isReadOnly: Bool = false
	var hasPendingSync: Bool = false
	var isOffline: Bool = false
	var isSyncing: Bool = false

	// 모달 및 폼 상태
	var isItemSheetPresented: Bool = false
	var isDeleteAlertPresented: Bool = false
	var itemToDelete: VaultItem? = nil
	var editingItem: VaultItem? = nil

	var formServiceName: String = ""
	var formUsername: String = ""
	var formPassword: String = ""
	var formUrl: String = ""
	var formNotes: String = ""
	var formErrorMessage: String? = nil

	func isPasswordRevealed(id: String) -> Bool {
		revealedPasswordIds.contains(id)
	}

	// MARK: - 항목 폼 강도 안내 (GEN-001). 약한 비밀번호도 저장은 허용한다

	var showsFormStrengthGauge: Bool {
		!formPassword.isEmpty
	}

	var formPasswordStrengthScore: Int {
		EntropyScorer.score(for: formPassword)
	}

	var showsFormWeakPasswordWarning: Bool {
		showsFormStrengthGauge && EntropyScorer.isWeak(score: formPasswordStrengthScore)
	}

	/// 점수와 별개로 안내만 한다
	var showsFormSequentialDigitsNotice: Bool {
		SequentialDigitsChecker.hasSequentialDigits(formPassword)
	}
}

enum VaultListViewAction: Equatable, Sendable {
	case viewDidLoad
	case search(String)
	case copyPassword(VaultItem)
	case copyUsername(VaultItem)
	case togglePasswordVisibility(id: String)
	case startCreatingItem
	case startEditingItem(VaultItem)
	case cancelEditing
	case generatePasswordForForm
	case updateFormServiceName(String)
	case updateFormUsername(String)
	case updateFormPassword(String)
	case updateFormUrl(String)
	case updateFormNotes(String)
	case setItemSheetPresented(Bool)
	case requestSaveItem
	case saveItem(
		existingId: String?,
		serviceName: String,
		username: String,
		password: String,
		url: String?,
		notes: String?
	)
	case confirmDeleteItem(VaultItem)
	case cancelDeleteItem
	case setDeleteAlertPresented(Bool)
	case executeDeleteItem
	case deleteItem(id: String)
	case requestSync
}
