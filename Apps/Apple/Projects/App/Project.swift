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
			sources: ["Sources/macOS/**"],
			dependencies: [
				.project(target: "KeyManageDomain", path: "../Core/Domain"),
				.project(target: "KeyManageData", path: "../Core/Data"),
				.project(target: "KeyManageDesignSystem", path: "../Core/DesignSystem"),
				.project(target: "KeyManageUIAdapter", path: "../Core/UIAdapter"),
				.project(target: "UnlockFeatureInterface", path: "../Features/Unlock"),
				.project(target: "UnlockFeatureImplementation", path: "../Features/Unlock"),
				.project(target: "RootFeatureInterface", path: "../Features/Root"),
				.project(target: "RootFeatureImplementation", path: "../Features/Root"),
				.external(name: "MicroRIBs")
			]
		),
		.target(
			name: "KeyManageIOS",
			destinations: [.iPhone, .iPad],
			product: .app,
			bundleId: "com.keymanage.ios",
			deploymentTargets: .iOS("15.0"),
			infoPlist: .default,
			sources: ["Sources/iOS/**"],
			dependencies: [
				.project(target: "KeyManageDomain", path: "../Core/Domain"),
				.project(target: "KeyManageData", path: "../Core/Data"),
				.project(target: "KeyManageDesignSystem", path: "../Core/DesignSystem"),
				.project(target: "KeyManageUIAdapter", path: "../Core/UIAdapter"),
				.project(target: "UnlockFeatureInterface", path: "../Features/Unlock"),
				.project(target: "UnlockFeatureImplementation", path: "../Features/Unlock"),
				.project(target: "RootFeatureInterface", path: "../Features/Root"),
				.project(target: "RootFeatureImplementation", path: "../Features/Root"),
				.external(name: "MicroRIBs")
			]
		)
	]
)
