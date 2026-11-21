import ProjectDescription

/// 공유 Kotlin RIB을 쓰는 피처의 Swift 타깃 (modularization-plan F단계 설계).
/// 뷰(`Sources`)는 프레임워크로 만들지 않고 쓰는 곳이 직접 컴파일한다. 앱은 운영용, Example·뷰 테스트는 테스트용 묶음을 링크한다.
/// 두 묶음은 이름(KeyManageShared)이 같아 뷰 코드는 그대로 컴파일된다
public enum SharedFeature {
	public static let iOSVersion = "17.0"

	/// 운영용 묶음. 앱이 링크한다. 먼저 `Apps/Shared`에서 `./gradlew :apple-session:assembleKeyManageSharedDebugXCFramework`
	public static let productionFramework: TargetDependency = .xcframework(
		path: .relativeToRoot("../Shared/apple-session/build/XCFrameworks/debug/KeyManageShared.xcframework")
	)

	/// 테스트용 묶음. 먼저 `./gradlew :apple-example:assembleKeyManageSharedDebugXCFramework`
	public static let testingFramework: TargetDependency = .xcframework(
		path: .relativeToRoot("../Shared/apple-example/build/XCFrameworks/debug/KeyManageShared.xcframework")
	)

	static let viewDependencies: [TargetDependency] = [
		.project(target: "KeyManageDesignSystem", path: .relativeToRoot("Projects/Core/DesignSystem")),
		.external(name: "RIBsKMPSwiftUI")
	]

	/// `<name>Example`(시나리오 목록 앱)과 `<name>ViewTests`(뷰 테스트).
	/// - Parameter childFeatures: 자식 RIB을 띄우는 피처가 함께 컴파일할 자식 피처 폴더 이름(VaultList → PasswordGenerator 등)
	public static func targets(name: String, childFeatures: [String] = []) -> [Target] {
		let lower = name.lowercased()
		let example = Target.target(
			name: "\(name)Example",
			destinations: [.iPhone, .iPad],
			product: .app,
			bundleId: "com.keymanage.\(lower).example",
			deploymentTargets: .iOS(iOSVersion),
			infoPlist: .extendingDefault(with: ["UILaunchScreen": [:]]),
			sources: .sourceFilesList(globs: ["Sources/**", "Example/**", "../../Core/SharedUI/Sources/**"] + childFeatures.map { "../\($0)/Sources/**" }),
			dependencies: viewDependencies + [
				testingFramework,
				.project(target: "KeyManageFeatureExample", path: .relativeToRoot("Projects/Core/FeatureExample"))
			]
		)
		let tests = Target.target(
			name: "\(name)ViewTests",
			destinations: [.iPhone, .iPad],
			product: .unitTests,
			bundleId: "com.keymanage.\(lower).viewtests",
			deploymentTargets: .iOS(iOSVersion),
			sources: ["ViewTests/**"],
			dependencies: [.target(name: "\(name)Example")]
		)
		return [example, tests]
	}
}
