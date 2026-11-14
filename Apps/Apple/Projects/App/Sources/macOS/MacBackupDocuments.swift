import AppKit
import KeyManageShared
import UniformTypeIdentifiers

/// 백업 파일 저장·열기 패널 (VLT-004). 공유 코드(`DocumentsBackupFiles`)가 임시 폴더에 저장한 파일을 저장 패널로 고른 곳에 복사하고,
/// 복원할 파일은 열기 패널로 고르게 한다. 모든 호출은 메인 액터에서 한다
@MainActor
final class MacBackupDocuments: NSObject, @preconcurrency BackupDocumentPresenter {
	private var pending: ((String?) -> Void)?

	func pickBackupDocument(onPicked: @escaping (String?) -> Void) {
		// 앞선 선택이 끝나지 않았으면 그 요청은 취소로 끝낸다
		pending?(nil)
		pending = onPicked
		let panel = NSOpenPanel()
		panel.allowedContentTypes = [.json, .plainText, .data]
		panel.allowsMultipleSelection = false
		panel.canChooseDirectories = false
		panel.begin { [weak self] response in
			MainActor.assumeIsolated {
				self?.finishPick(response == .OK ? panel.url : nil)
			}
		}
	}

	func exportBackupDocument(path: String) {
		let source = URL(fileURLWithPath: path)
		let panel = NSSavePanel()
		panel.nameFieldStringValue = source.lastPathComponent
		panel.canCreateDirectories = true
		panel.begin { response in
			MainActor.assumeIsolated {
				defer { try? FileManager.default.removeItem(at: source) }
				guard response == .OK, let destination = panel.url else { return }
				Self.copy(source, to: destination)
			}
		}
	}

	private func finishPick(_ url: URL?) {
		let callback = pending
		pending = nil
		callback?(url?.path)
	}

	/// 저장 패널이 덮어쓰기를 이미 확인했으므로 있던 파일은 바꾼다
	private static func copy(_ source: URL, to destination: URL) {
		do {
			if FileManager.default.fileExists(atPath: destination.path) {
				_ = try FileManager.default.replaceItemAt(destination, withItemAt: source, options: .usingNewMetadataOnly)
			} else {
				try FileManager.default.copyItem(at: source, to: destination)
			}
		} catch {
			NSAlert(error: error).runModal()
		}
	}
}
