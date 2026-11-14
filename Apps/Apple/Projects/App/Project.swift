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
		// macOS 앱도 iOS와 같은 공유 Kotlin RIB 트리를 쓴다 (domain-migration-plan 12.5 macOS 전환).
		// 화면 SwiftUI는 Sources/SharedUI를 iOS와 함께 컴파일하고, 배치 차이(사이드바·시트)는 #if os(macOS)로 나눈다
		.target(
			name: "KeyManageMac",
			destinations: [.mac],
			product: .app,
			bundleId: "com.keymanage.macos",
			// RIBsKMPSwiftUI의 최소 버전
			deploymentTargets: .macOS("14.0"),
			infoPlist: .extendingDefault(with: [
				// 생체 해제 (SEC-003). 공유 코드 KeychainBiometricUnlock이 LAContext(Touch ID)로 묻는다
				"NSFaceIDUsageDescription": "볼트 잠금을 생체 인증으로 해제합니다."
			]),
			sources: ["Sources/macOS/**", "Sources/SharedUI/**"],
			dependencies: [
				.project(target: "KeyManageDesignSystem", path: "../Core/DesignSystem"),
				// 실행 전에 Apps/Shared에서 `./gradlew :apple-app:assembleKeyManageSharedDebugXCFramework`로 만든다(macos-arm64 조각 포함)
				.xcframework(path: "../../../Shared/apple-app/build/XCFrameworks/debug/KeyManageShared.xcframework"),
				.package(product: "RIBsKMPSwiftUI")
			]
		),
		.target(
			name: "KeyManageIOS",
			destinations: [.iPhone, .iPad],
			product: .app,
			bundleId: "com.keymanage.ios",
			deploymentTargets: .iOS("17.0"),
			infoPlist: .extendingDefault(with: [
				"UILaunchScreen": [:],
				// 내보낸 백업 파일(앱 문서 폴더)을 파일 앱에서 볼 수 있게 한다 (VLT-004)
				"UIFileSharingEnabled": true,
				"LSSupportsOpeningDocumentsInPlace": true,
				// 생체 해제 (SEC-003). 공유 코드 KeychainBiometricUnlock이 LAContext로 묻는다
				"NSFaceIDUsageDescription": "볼트 잠금을 생체 인증으로 해제합니다."
			]),
			sources: ["Sources/iOS/**", "Sources/SharedUI/**"],
			dependencies: [
				.project(target: "KeyManageDesignSystem", path: "../Core/DesignSystem"),
				// 실행 전에 Apps/Shared에서 `./gradlew :apple-app:assembleKeyManageSharedDebugXCFramework`로 만든다
				.xcframework(path: "../../../Shared/apple-app/build/XCFrameworks/debug/KeyManageShared.xcframework"),
				.package(product: "RIBsKMPSwiftUI")
			]
		),
		// 공유 RIB 화면 흐름을 로컬 백엔드(localhost:8080)에 붙여 확인한다 (domain-migration-plan 12.3의 2·3단계)
		.target(
			name: "KeyManageIOSUITests",
			destinations: [.iPhone],
			product: .uiTests,
			bundleId: "com.keymanage.ios.uitests",
			deploymentTargets: .iOS("17.0"),
			infoPlist: .default,
			sources: ["UITests/iOS/**"],
			dependencies: [.target(name: "KeyManageIOS")]
		),
		// macOS 앱도 같은 흐름을 로컬 백엔드(localhost:8080)에 붙여 확인한다 (domain-migration-plan 12.5 macOS 전환)
		.target(
			name: "KeyManageMacUITests",
			destinations: [.mac],
			product: .uiTests,
			bundleId: "com.keymanage.macos.uitests",
			deploymentTargets: .macOS("14.0"),
			infoPlist: .default,
			sources: ["UITests/macOS/**"],
			dependencies: [.target(name: "KeyManageMac")]
		)
	]
)
