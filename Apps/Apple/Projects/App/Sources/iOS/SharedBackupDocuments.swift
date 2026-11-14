import KeyManageShared
import UIKit
import UniformTypeIdentifiers

/// 백업 파일 문서 선택기 (VLT-004). 공유 코드(`DocumentsBackupFiles`)가 저장한 파일을 내보내고, 복원할 파일을 고르게 한다.
/// 고른 파일은 보안 범위 URL이라 앱 임시 폴더에 복사해 경로를 넘긴다. 모든 호출은 메인 액터에서 한다
@MainActor
final class SharedBackupDocuments: NSObject, @preconcurrency BackupDocumentPresenter {
	private var pending: ((String?) -> Void)?
	private var pickerDelegate: PickerDelegate?

	func pickBackupDocument(onPicked: @escaping (String?) -> Void) {
		// 앞선 선택이 끝나지 않았으면 그 요청은 취소로 끝낸다
		pending?(nil)
		pending = onPicked
		let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText, .data], asCopy: true)
		let delegate = PickerDelegate { [weak self] url in self?.finishPick(url) }
		pickerDelegate = delegate
		picker.delegate = delegate
		picker.allowsMultipleSelection = false
		guard present(picker) else {
			finishPick(nil)
			return
		}
	}

	func exportBackupDocument(path: String) {
		let picker = UIDocumentPickerViewController(forExporting: [URL(fileURLWithPath: path)], asCopy: true)
		_ = present(picker)
	}

	private func finishPick(_ url: URL?) {
		let callback = pending
		pending = nil
		pickerDelegate = nil
		callback?(url.flatMap(Self.copyToTemporary))
	}

	/// `asCopy`로 고른 파일은 앱 Inbox에 복사돼 오지만, 보안 범위 접근이 필요한 경우를 대비해 임시 폴더로 한 번 더 옮긴다
	private static func copyToTemporary(_ url: URL) -> String? {
		let scoped = url.startAccessingSecurityScopedResource()
		defer { if scoped { url.stopAccessingSecurityScopedResource() } }
		let destination = FileManager.default.temporaryDirectory
			.appendingPathComponent(UUID().uuidString)
			.appendingPathExtension(url.pathExtension)
		do {
			try FileManager.default.copyItem(at: url, to: destination)
			return destination.path
		} catch {
			return nil
		}
	}

	private func present(_ controller: UIViewController) -> Bool {
		let scene = UIApplication.shared.connectedScenes
			.compactMap { $0 as? UIWindowScene }
			.first { $0.activationState == .foregroundActive } ?? UIApplication.shared.connectedScenes.first as? UIWindowScene
		guard var top = scene?.keyWindow?.rootViewController else { return false }
		while let presented = top.presentedViewController { top = presented }
		top.present(controller, animated: true)
		return true
	}
}

private final class PickerDelegate: NSObject, UIDocumentPickerDelegate {
	private let onFinish: @MainActor (URL?) -> Void

	init(onFinish: @escaping @MainActor (URL?) -> Void) {
		self.onFinish = onFinish
	}

	func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
		MainActor.assumeIsolated { onFinish(urls.first) }
	}

	func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
		MainActor.assumeIsolated { onFinish(nil) }
	}
}
