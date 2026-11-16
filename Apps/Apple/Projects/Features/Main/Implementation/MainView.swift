import SwiftUI
import MicroRIBs
import KeyManageDesignSystem
import MainFeatureInterface

struct MainView: View {
	@ObservedObject var viewModel: MainViewModel

	init(viewModel: MainViewModel) {
		self.viewModel = viewModel
	}

	var body: some View {
		#if os(macOS)
		macOSLayout
		#elseif os(iOS)
		iOSLayout
		#endif
	}

	// MARK: - macOS 2-Pane Navigation Split Layout

	#if os(macOS)
	private var macOSLayout: some View {
		let selectionBinding = Binding<MainTab?>(
			get: { viewModel.state.selectedTab },
			set: { if let tab = $0 { viewModel.request(action: .selectTab(tab)) } }
		)

		return NavigationView {
			List {
				Section("메뉴") {
					ForEach(MainTab.allCases) { tab in
						NavigationLink(
							destination: tabContent(for: tab),
							tag: tab,
							selection: selectionBinding
						) {
							HStack {
								Image(systemName: iconName(for: tab))
								Text(tab.title)
							}
						}
					}
				}
			}
			.listStyle(.sidebar)
			.frame(minWidth: 180)
			.toolbar {
				ToolbarItem(placement: .automatic) {
					KMButton(
						"볼트 잠금",
						variant: .destructive,
						size: .sm,
						action: { viewModel.request(action: .lockVault) }
					)
				}
			}

			tabContent(for: viewModel.state.selectedTab)
		}
	}
	#endif

	// MARK: - iOS TabView Layout

	#if os(iOS)
	private var iOSLayout: some View {
		let tabBinding = Binding<MainTab>(
			get: { viewModel.state.selectedTab },
			set: { viewModel.request(action: .selectTab($0)) }
		)

		return TabView(selection: tabBinding) {
			ForEach(MainTab.allCases) { tab in
				navigationContainer {
					tabContent(for: tab)
						.navigationTitle(tab.title)
						.toolbar {
							ToolbarItem(placement: .topBarTrailing) {
								Button {
									viewModel.request(action: .lockVault)
								} label: {
									Image(systemName: "lock")
								}
								.accessibilityLabel("볼트 잠금")
							}
						}
				}
				.tabItem {
					Label(tab.title, systemImage: iconName(for: tab))
				}
				.tag(tab)
			}
		}
	}

	@ViewBuilder
	private func navigationContainer<Content: View>(@ViewBuilder content: () -> Content) -> some View {
		if #available(iOS 16.0, *) {
			NavigationStack {
				content()
			}
		} else {
			NavigationView {
				content()
			}
			.navigationViewStyle(.stack)
		}
	}
	#endif

	// MARK: - Content Helper

	@ViewBuilder
	private func tabContent(for tab: MainTab) -> some View {
		switch tab {
		case .vault:
			if let vc = viewModel.vaultListViewControllable {
				ViewControllableRepresentable(viewControllable: vc)
			} else {
				placeholder(for: tab)
			}
		case .settings:
			if let vc = viewModel.settingsViewControllable {
				ViewControllableRepresentable(viewControllable: vc)
			} else {
				placeholder(for: tab)
			}
		case .generator:
			if let vc = viewModel.passwordGeneratorViewControllable {
				ViewControllableRepresentable(viewControllable: vc)
			} else {
				placeholder(for: tab)
			}
		}
	}

	@ViewBuilder
	private func placeholder(for tab: MainTab) -> some View {
		VStack(spacing: DesignToken.Spacing.space4) {
			Image(systemName: iconName(for: tab))
				.font(.system(size: 48))
				.foregroundColor(DesignToken.View.primary)

			Text(tab.title)
				.font(DesignToken.Typography.title.weight(.bold))
				.foregroundColor(DesignToken.View.foreground)

			Text("\(tab.title) 화면이 준비 중입니다.")
				.font(DesignToken.Typography.label)
				.foregroundColor(DesignToken.View.mutedForeground)
		}
		.frame(maxWidth: .infinity, maxHeight: .infinity)
		.background(DesignToken.View.background)
	}

	private func iconName(for tab: MainTab) -> String {
		switch tab {
		case .vault:
			return "lock.shield"
		case .generator:
			return "key.horizontal"
		case .settings:
			return "gearshape"
		}
	}
}

// MARK: - ViewControllableRepresentable

#if os(macOS)
import AppKit

struct ViewControllableRepresentable: NSViewControllerRepresentable {
	let viewControllable: ViewControllable

	func makeNSViewController(context: Context) -> NSViewController {
		viewControllable.nsViewController
	}

	func updateNSViewController(_ nsViewController: NSViewController, context: Context) {}
}
#elseif os(iOS)
import UIKit

struct ViewControllableRepresentable: UIViewControllerRepresentable {
	let viewControllable: ViewControllable

	func makeUIViewController(context: Context) -> UIViewController {
		viewControllable.uiViewController
	}

	func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
#endif
