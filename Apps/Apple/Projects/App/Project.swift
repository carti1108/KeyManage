import ProjectDescription
import ProjectDescriptionHelpers

let project = Project(
	name: "KeyManageApp",
	settings: .swift6Settings,
	targets: [
		// macOS 앱도 공유 Kotlin RIB 트리로 돈다. 뷰는 iOS와 같은 피처 뷰를 함께 컴파일한다
		.target(
			name: "KeyManageMac",
			destinations: [.mac],
			product: .app,
			bundleId: "com.keymanage.macos",
			deploymentTargets: .macOS("14.0"),
			infoPlist: .extendingDefault(with: [
				"KeyManageServerURL": "$(KEYMANAGE_SERVER_URL)",
				"NSAppTransportSecurity": ["NSAllowsLocalNetworking": true]
			]),
			sources: .sourceFilesList(globs: [
				"Sources/macOSShared/**",
				"Sources/AppleShared/**",
				"../Features/*/Sources/**",
				"../Core/SharedUI/Sources/**"
			]),
			dependencies: [
				SharedFeature.productionFramework,
				.project(target: "KeyManageDesignSystem", path: "../Core/DesignSystem"),
				.external(name: "RIBsKMPSwiftUI")
			],
			settings: .settings(base: ["KEYMANAGE_SERVER_URL": "http://localhost:8080"])
		),
		// iOS 앱은 공유 Kotlin RIB 트리로 돈다. 피처 뷰는 프레임워크로 만들지 않고 여기서 함께 컴파일한다
		.target(
			name: "KeyManageIOS",
			destinations: [.iPhone, .iPad],
			product: .app,
			bundleId: "com.keymanage.ios",
			deploymentTargets: .iOS(SharedFeature.iOSVersion),
			infoPlist: .extendingDefault(with: [
				"UILaunchScreen": [:],
				"KeyManageServerURL": "$(KEYMANAGE_SERVER_URL)",
				"NSFaceIDUsageDescription": "생체 인증으로 볼트 잠금을 풉니다.",
				// 기본 서버는 이 Mac의 로컬 백엔드(http://localhost:8080)다. 그 밖의 서버는 HTTPS만 쓴다 (공유 resolveBaseUrl)
				"NSAppTransportSecurity": ["NSAllowsLocalNetworking": true]
			]),
			sources: .sourceFilesList(globs: [
				"Sources/iOSShared/**",
				"Sources/AppleShared/**",
				"../Features/*/Sources/**",
				"../Core/SharedUI/Sources/**"
			]),
			dependencies: [
				SharedFeature.productionFramework,
				.project(target: "KeyManageDesignSystem", path: "../Core/DesignSystem"),
				.external(name: "RIBsKMPSwiftUI")
			],
			settings: .settings(base: ["KEYMANAGE_SERVER_URL": "http://localhost:8080"])
		),
		.target(
			name: "KeyManageIOSUITests",
			destinations: [.iPhone, .iPad],
			product: .uiTests,
			bundleId: "com.keymanage.ios.uitests",
			deploymentTargets: .iOS(SharedFeature.iOSVersion),
			sources: ["UITests/Shared/**"],
			dependencies: [.target(name: "KeyManageIOS")]
		),
		// iOS와 같은 흐름을 macOS 앱에서 본다
		.target(
			name: "KeyManageMacUITests",
			destinations: [.mac],
			product: .uiTests,
			bundleId: "com.keymanage.macos.uitests",
			deploymentTargets: .macOS("14.0"),
			sources: ["UITests/Shared/**"],
			dependencies: [.target(name: "KeyManageMac")]
		)
	],
	schemes: [
		// 앱과 앱 UI 테스트만 빌드한다
		.scheme(
			name: "KeyManageIOS",
			buildAction: .buildAction(targets: ["KeyManageIOS"]),
			testAction: .targets(["KeyManageIOSUITests"]),
			runAction: .runAction(executable: "KeyManageIOS")
		),
		.scheme(
			name: "KeyManageMac",
			buildAction: .buildAction(targets: ["KeyManageMac"]),
			testAction: .targets(["KeyManageMacUITests"]),
			runAction: .runAction(executable: "KeyManageMac")
		)
	]
)
