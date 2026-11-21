// swift-tools-version: 6.0
import PackageDescription

#if TUIST
import ProjectDescription

let packageSettings = PackageSettings(
	productTypes: [
		"MicroRIBs": .framework,
		"RIBsKMPSwiftUI": .staticFramework
	],
	targetSettings: [
		"AsyncAlgorithms": ["MACOSX_DEPLOYMENT_TARGET": "14.0", "IPHONEOS_DEPLOYMENT_TARGET": "15.0"],
		"InternalCollectionsUtilities": ["MACOSX_DEPLOYMENT_TARGET": "14.0", "IPHONEOS_DEPLOYMENT_TARGET": "15.0"],
		"OrderedCollections": ["MACOSX_DEPLOYMENT_TARGET": "14.0", "IPHONEOS_DEPLOYMENT_TARGET": "15.0"],
		"DequeModule": ["MACOSX_DEPLOYMENT_TARGET": "14.0", "IPHONEOS_DEPLOYMENT_TARGET": "15.0"],
		"SpanPreview": ["MACOSX_DEPLOYMENT_TARGET": "14.0", "IPHONEOS_DEPLOYMENT_TARGET": "15.0"],
		"MicroRIBs": ["MACOSX_DEPLOYMENT_TARGET": "14.0", "IPHONEOS_DEPLOYMENT_TARGET": "15.0"]
	]
)
#endif

let package = Package(
	name: "KeyManagePackageDependencies",
	dependencies: [
		.package(url: "https://github.com/carti1108/MicroRIBs.git", branch: "main"),
		// 공유 Kotlin RIB 화면 자리. carti-RIBs-KMP 형제 폴더에서 가져온다 (modularization-plan F단계)
		.package(path: "../../../../carti-RIBs-KMP/swift/RIBsKMPSwiftUI")
	]
)
