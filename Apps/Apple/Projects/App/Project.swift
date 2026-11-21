import ProjectDescription
import ProjectDescriptionHelpers

let project = Project(
	name: "KeyManageApp",
	settings: .swift6Settings,
	targets: [
		.target(
			name: "KeyManageMac",
			destinations: [.mac],
			product: .app,
			bundleId: "com.keymanage.macos",
			deploymentTargets: .macOS("14.0"),
			infoPlist: .default,
			sources: ["Sources/macOS/**", "Sources/Shared/**"],
			dependencies: [
				.project(target: "KeyManageDomain", path: "../Core/Domain"),
				.project(target: "KeyManageData", path: "../Core/Data"),
				.project(target: "KeyManageDesignSystem", path: "../Core/DesignSystem"),
				.project(target: "KeyManageUIAdapter", path: "../Core/UIAdapter"),
				.project(target: "UnlockFeatureInterface", path: "../Features/Unlock"),
				.project(target: "UnlockFeatureImplementation", path: "../Features/Unlock"),
				.project(target: "MainFeatureInterface", path: "../Features/Main"),
				.project(target: "MainFeatureImplementation", path: "../Features/Main"),
				.project(target: "VaultListFeatureInterface", path: "../Features/VaultList"),
				.project(target: "VaultListFeatureImplementation", path: "../Features/VaultList"),
				.project(target: "SettingsFeatureInterface", path: "../Features/Settings"),
				.project(target: "SettingsFeatureImplementation", path: "../Features/Settings"),
				.project(target: "PasswordGeneratorFeatureInterface", path: "../Features/PasswordGenerator"),
				.project(target: "PasswordGeneratorFeatureImplementation", path: "../Features/PasswordGenerator"),
				.project(target: "RootFeatureInterface", path: "../Features/Root"),
				.project(target: "RootFeatureImplementation", path: "../Features/Root"),
				.project(target: "AuthFeatureInterface", path: "../Features/Auth"),
				.project(target: "AuthFeatureImplementation", path: "../Features/Auth"),
				.external(name: "MicroRIBs")
			]
		),
		// iOS 앱은 공유 Kotlin RIB 트리로 돈다. 피처 뷰는 프레임워크로 만들지 않고 여기서 함께 컴파일한다 (modularization-plan F단계 ④)
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
			sources: ["UITests/iOS/**"],
			dependencies: [.target(name: "KeyManageIOS")]
		)
	],
	schemes: [
		// 앱과 앱 UI 테스트만 빌드한다. 옛 Swift 피처 타깃은 macOS 앱이 옮겨 갈 때까지만 남는다
		.scheme(
			name: "KeyManageIOS",
			buildAction: .buildAction(targets: ["KeyManageIOS"]),
			testAction: .targets(["KeyManageIOSUITests"]),
			runAction: .runAction(executable: "KeyManageIOS")
		)
	]
)
