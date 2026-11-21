// swift-tools-version: 6.0
import PackageDescription

#if TUIST
import ProjectDescription

let packageSettings = PackageSettings(
	productTypes: [
		"RIBsKMPSwiftUI": .staticFramework
	]
)
#endif

let package = Package(
	name: "KeyManagePackageDependencies",
	dependencies: [
		// 공유 Kotlin RIB 화면 자리. carti-RIBs-KMP 형제 폴더에서 가져온다 (modularization-plan F단계)
		.package(path: "../../../../carti-RIBs-KMP/swift/RIBsKMPSwiftUI")
	]
)
