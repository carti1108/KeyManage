import SwiftUI

@MainActor
public final class KMPasswordFieldEngine: ObservableObject {
    @Published public var text: String
    @Published public var isSecured: Bool
    public var placeholder: String
    public var showCopyAction: Bool
    public var onCopy: (() -> Void)?
    public var onTextChange: ((String) -> Void)?

    @Published public private(set) var autoMaskSecondsRemaining: Int = 0

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
    public var showCopyAction: Bool

    @ObservedObject public var engine: KMPasswordFieldEngine

    public init(
        text: Binding<String>,
        placeholder: String = "비밀번호 입력",
        isSecured: Bool = true,
        showCopyAction: Bool = true,
        onCopy: (() -> Void)? = nil,
        onTextChange: ((String) -> Void)? = nil,
        engine: KMPasswordFieldEngine? = nil
    ) {
        self._text = text
        self.placeholder = placeholder
        self.showCopyAction = showCopyAction
        if let engine = engine {
            self.engine = engine
        } else {
            self.engine = KMPasswordFieldEngine(
                text: text.wrappedValue,
                placeholder: placeholder,
                isSecured: isSecured,
                showCopyAction: showCopyAction,
                onCopy: onCopy,
                onTextChange: onTextChange
            )
        }
    }

    public var isSecured: Bool {
        engine.isSecured
    }

    public var body: some View {
        HStack(spacing: 8) {
            if engine.isSecured {
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
                engine.toggleSecured()
            }) {
                Image(systemName: engine.isSecured ? DesignToken.Icon.passwordShow : DesignToken.Icon.passwordHide)
                    .foregroundColor(DesignToken.View.mutedForeground)
                    .frame(width: 24, height: 24)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(engine.isSecured ? "비밀번호 표시" : "비밀번호 숨김")

            if showCopyAction {
                Button(action: {
                    engine.copyAction()
                }) {
                    Image(systemName: DesignToken.Icon.clipboardCopy)
                        .foregroundColor(DesignToken.View.mutedForeground)
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("비밀번호 복사")
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .background(DesignToken.View.card)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(
            RoundedRectangle(cornerRadius: 6)
                .stroke(DesignToken.View.input, lineWidth: 1)
        )
        .onChange(of: text) { _, newValue in
            engine.updateText(newValue)
        }
        .task(id: engine.isSecured) {
            guard !engine.isSecured else { return }
            while !Task.isCancelled && !engine.isSecured && engine.autoMaskSecondsRemaining > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                engine.advanceTime(seconds: 1)
            }
        }
    }
}
