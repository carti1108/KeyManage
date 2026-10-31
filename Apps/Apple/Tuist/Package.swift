// swift-tools-version: 6.0
import PackageDescription

#if TUIST
import ProjectDescription

let packageSettings = PackageSettings(
	productTypes: [
		"MicroRIBs": .framework
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
		.package(url: "https://github.com/carti1108/MicroRIBs.git", branch: "main")
	]
)
