import ProjectDescription
import ProjectDescriptionHelpers

let project = Project(
	name: "KeyManageApp",
	// 공유 RIB용 SwiftUI 어댑터(carti-RIBs-KMP). 기본은 저장소와 같은 상위 폴더의 형제 폴더이고,
	// CI처럼 다른 곳에 받으면 TUIST_CARTI_RIBS_DIR(절대 경로)로 알려 준다 (domain-migration-plan 12.2)
	packages: [
		.local(path: .path("\(Environment.cartiRibsDir.getString(default: "../../../../../carti-RIBs-KMP"))/swift/RIBsKMPSwiftUI"))
	],
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
		.target(
			name: "KeyManageIOS",
			destinations: [.iPhone, .iPad],
			product: .app,
			bundleId: "com.keymanage.ios",
			deploymentTargets: .iOS("17.0"),
			infoPlist: .extendingDefault(with: [
				"UILaunchScreen": [:]
			]),
			sources: ["Sources/iOS/**", "Sources/Shared/**"],
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
				.external(name: "MicroRIBs"),
				// 실행 전에 Apps/Shared에서 `./gradlew :ios-app:assembleKeyManageSharedDebugXCFramework`로 만든다
				.xcframework(path: "../../../Shared/ios-app/build/XCFrameworks/debug/KeyManageShared.xcframework"),
				.package(product: "RIBsKMPSwiftUI")
			]
		)
	]
)
