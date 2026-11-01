import SwiftUI

public final class KMPasswordFieldEngine: ObservableObject {
    @Published public var text: String
    @Published public var isSecured: Bool
    public var placeholder: String
    public var showCopyAction: Bool
    public var onCopy: (() -> Void)?
    public var onTextChange: ((String) -> Void)?

    public private(set) var autoMaskSecondsRemaining: Int = 0

    public init(
        text: String = "",
        placeholder: String = "비밀번호 입력",
        isSecured: Bool = true,
        showCopyAction: Bool = true,
        onCopy: (() -> Void)? = nil,
        onTextChange: ((String) -> Void)? = nil
    ) {
        self.text = text
        self.placeholder = placeholder
        self.isSecured = isSecured
        self.showCopyAction = showCopyAction
        self.onCopy = onCopy
        self.onTextChange = onTextChange
    }

    public func toggleSecured() {
        // RED STUB: does not switch or start 10s countdown
    }

    public func copyAction() {
        // RED STUB: does not invoke onCopy
    }

    public func updateText(_ newText: String) {
        // RED STUB: does not reset 10s countdown
        self.text = newText
    }

    public func advanceTime(seconds: Int) {
        // RED STUB: does not countdown or revert isSecured to true
    }
}

public struct KMPasswordField: View {
    @Binding public var text: String
    public var placeholder: String
    public var isSecured: Bool
    public var showCopyAction: Bool
    public var onCopy: (() -> Void)?
    public var onTextChange: ((String) -> Void)?

    public init(
        text: Binding<String>,
        placeholder: String = "비밀번호 입력",
        isSecured: Bool = true,
        showCopyAction: Bool = true,
        onCopy: (() -> Void)? = nil,
        onTextChange: ((String) -> Void)? = nil
    ) {
        self._text = text
        self.placeholder = placeholder
        self.isSecured = isSecured
        self.showCopyAction = showCopyAction
        self.onCopy = onCopy
        self.onTextChange = onTextChange
    }

    public var body: some View {
        // RED STUB
        EmptyView()
    }
}
