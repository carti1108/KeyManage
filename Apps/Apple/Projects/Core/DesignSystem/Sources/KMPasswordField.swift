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
        isSecured.toggle()
        if !isSecured {
            autoMaskSecondsRemaining = 10
        } else {
            autoMaskSecondsRemaining = 0
        }
    }

    public func copyAction() {
        onCopy?()
    }

    public func updateText(_ newText: String) {
        self.text = newText
        onTextChange?(newText)
        if !isSecured {
            autoMaskSecondsRemaining = 10
        }
    }

    public func advanceTime(seconds: Int) {
        guard !isSecured, autoMaskSecondsRemaining > 0 else { return }
        autoMaskSecondsRemaining = max(0, autoMaskSecondsRemaining - seconds)
        if autoMaskSecondsRemaining == 0 {
            isSecured = true
        }
    }
}

public struct KMPasswordField: View {
    @Binding public var text: String
    public var placeholder: String
    @State public var isSecured: Bool
    public var showCopyAction: Bool
    public var onCopy: (() -> Void)?
    public var onTextChange: ((String) -> Void)?

    @StateObject private var engine: KMPasswordFieldEngine

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
        self._isSecured = State(initialValue: isSecured)
        self.showCopyAction = showCopyAction
        self.onCopy = onCopy
        self.onTextChange = onTextChange
        self._engine = StateObject(wrappedValue: KMPasswordFieldEngine(
            text: text.wrappedValue,
            placeholder: placeholder,
            isSecured: isSecured,
            showCopyAction: showCopyAction,
            onCopy: onCopy,
            onTextChange: onTextChange
        ))
    }

    public var body: some View {
        HStack(spacing: 8) {
            if isSecured {
                SecureField(placeholder, text: $text)
                    .textFieldStyle(.plain)
                    .font(DesignToken.Typography.body)
                    .foregroundColor(DesignToken.View.foreground)
            } else {
                TextField(placeholder, text: $text)
                    .textFieldStyle(.plain)
                    .font(DesignToken.Typography.passwordMonospaced)
                    .foregroundColor(DesignToken.View.foreground)
            }

            Button(action: {
                isSecured.toggle()
                engine.toggleSecured()
            }) {
                Image(systemName: isSecured ? DesignToken.Icon.passwordShow : DesignToken.Icon.passwordHide)
                    .foregroundColor(DesignToken.View.mutedForeground)
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.plain)

            if showCopyAction {
                Button(action: {
                    onCopy?()
                    engine.copyAction()
                }) {
                    Image(systemName: DesignToken.Icon.clipboardCopy)
                        .foregroundColor(DesignToken.View.mutedForeground)
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .background(DesignToken.View.card)
        .cornerRadius(6)
        .overlay(
            RoundedRectangle(cornerRadius: 6)
                .stroke(DesignToken.View.input, lineWidth: 1)
        )
        .onChange(of: text) { _, newValue in
            onTextChange?(newValue)
            engine.updateText(newValue)
        }
    }
}
