import RIBsKMPSwiftUI
import SwiftUI

/// 시나리오 하나로 띄운 RIB. `stop`은 RIB 트리를 내린다
@MainActor
public struct RunningExample {
	public let screen: SwiftUIRenderable
	public let stop: () -> Void

	public init(screen: SwiftUIRenderable, stop: @escaping () -> Void) {
		self.screen = screen
		self.stop = stop
	}
}

/// 피처 Example 앱의 공통 틀(Android core/example의 FeatureExample 짝).
/// 시나리오 목록을 보이고, 고르면 `launch`로 RIB 하나를 띄운다. 아래에는 RIB이 부모에 알린 결과를 쌓아 보인다.
/// Kotlin 프레임워크에 의존하지 않아 피처마다 `launch`만 쓴다
public struct FeatureExample: View {
	private let title: String
	private let scenarios: [String]
	private let launch: @MainActor (Int, @escaping (String) -> Void) -> RunningExample

	public init(
		title: String,
		scenarios: [String],
		launch: @escaping @MainActor (Int, @escaping (String) -> Void) -> RunningExample
	) {
		self.title = title
		self.scenarios = scenarios
		self.launch = launch
	}

	public var body: some View {
		NavigationStack {
			List(Array(scenarios.enumerated()), id: \.offset) { index, name in
				NavigationLink(name) {
					ScenarioScreen(index: index, launch: launch).navigationTitle(name)
				}
				.accessibilityIdentifier("scenario.\(index)")
			}
			.navigationTitle(title)
		}
	}
}

private struct ScenarioScreen: View {
	let index: Int
	let launch: @MainActor (Int, @escaping (String) -> Void) -> RunningExample
	@State private var running: RunningExample?
	@State private var events: [String] = []

	var body: some View {
		VStack(spacing: 0) {
			if let running {
				running.screen.makeView().id(ObjectIdentifier(running.screen))
			}
			if !events.isEmpty {
				VStack(alignment: .leading, spacing: 4) {
					Text("부모에 알린 결과").font(.caption.bold())
					ForEach(Array(events.enumerated()), id: \.offset) { _, event in
						Text(event).font(.caption.monospaced()).accessibilityIdentifier("example.event")
					}
				}
				.frame(maxWidth: .infinity, alignment: .leading)
				.padding(12)
				.background(.thinMaterial)
			}
		}
		.onAppear {
			guard running == nil else { return }
			running = launch(index) { events.append($0) }
		}
		.onDisappear {
			running?.stop()
			running = nil
		}
	}
}
