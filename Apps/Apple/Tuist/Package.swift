// swift-tools-version: 6.0
import PackageDescription

// 외부 Swift 패키지 의존은 없다. 공유 RIB 어댑터 RIBsKMPSwiftUI는 Projects/App/Project.swift가 로컬 패키지로 넣는다
// (MicroRIBs는 domain-migration-plan 12.5 4단계에서 옛 Swift RIB과 함께 뺐다)
let package = Package(
	name: "KeyManagePackageDependencies",
	dependencies: []
)
