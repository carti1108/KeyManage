# 2026-11-14 · Apple 앱을 공유 Kotlin RIB 트리로 전환 (연구 브랜치)

- 이슈·PR: 없음 (연구 브랜치 `research/kmp-migration`)
- 커밋: 3919558, 9846dba, 82181c0, 0ee69eb, 2099c09, 56fd82b, 03e60ec

> **요약** iOS·macOS 앱이 Swift microRIBs 대신 공유 Kotlin RIB 트리(XCFramework `KeyManageShared`)를 띄우게 하고, 옛 Swift RIB·도메인을 지웠다. 단계마다 구현 에이전트와 별도의 독립 검증 에이전트를 돌렸고, 검증이 오진·게이트 우회·정책 회귀를 잡았다. 사람은 생체 해제에 마스터 비밀번호가 아니라 유도 키를 두기로 정했다(ADR-0027).

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5). 오케스트레이터 1개가 단계를 나누고, 단계마다 Opus 서브에이전트로 구현과 독립 검증(읽기 전용 리뷰)을 따로 돌렸다. iOS 시뮬레이터 제어 도구.
- 플러그인·스킬: `adr-manager`, `ai-usage`, `policy-manager`, `ribs-architecture`

## 요청

연구 브랜치에서 Apple 앱을 공유 Kotlin RIB으로 옮기는 계획([domain-migration-plan.md 12장](../../research/kmp-migration/domain-migration-plan.md))을 0단계(XCFramework 연결)부터 4단계(옛 Swift 코드 삭제)까지 진행하고, 단계마다 독립 검증을 받는다.

## AI 산출물

- 0단계: `KeyManageShared` XCFramework와 `RIBsKMPSwiftUI` 연결. iOS 최소 버전 17.0.
- 1단계: 기기 저장소·클립보드·스케줄러·자동 잠금 설정·네트워크 상태 포트의 Kotlin 구현.
- 2단계: iOS 진입점(`IosKeyManageApp`, `AppLifecycleWiring`)과 UI 테스트.
- 3단계: 공유 RIB 화면 7개, 생체 해제 포트 `BiometricUnlock`와 키체인 구현.
- macOS 전환: XCFramework에 macosArm64 추가, `Sources/SharedUI` 공용 화면, 단일 `Window` 장면, macOS 백그라운드 판정. 최소 버전 14.0.
- 4단계: Swift `Features/*`·`Core/Domain`·`Core/Data`·`Core/UIAdapter` 삭제, 모듈 이름 `apple-app`, 정적 검사 스크립트 갱신.
- 마무리: `verify-tdd-integrity.sh`에 테스트 파일을 테스트가 아닌 이름으로 바꾸기와 테스트 비활성화 표시 추가를 위반으로 더함. [ADR-APL-002](../../../Apps/Apple/docs/adr/ADR-APL-002-apple-apps-host-shared-kotlin-ribs.md) 작성(ADR-0014·0015 대체).

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 수정 | 3단계 첫 구현은 생체 해제용으로 마스터 비밀번호를 키체인에 뒀다. 사람이 옛 Swift처럼 유도한 암호화 키를 두게 정했다([ADR-0027](../../adr/ADR-0027-biometric-unlock-stores-derived-key.md)) |
| 확인 | 3단계 UI 테스트 실패 때 사람이 시뮬레이터에서 복사해도 잠기지 않고 검색·생성기·자동 잠금·백업이 동작함을 직접 확인했다 |
| 보류 | 잠글 때 키 바이트를 0으로 지울지(동기화 뒤 지우기 설계 필요)는 정하지 않았다 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `:domain:jvmTest`·`:feature:jvmTest`·`:apple-app:allTests` | 통과 (418·345·84건, 4단계 마지막) |
| 웹 `npm run check` | 통과 (499건) |
| `Scripts/verify-apple.sh` (문서·린트·정책·API·TDD 무결성·두 앱 빌드·DesignSystem 테스트) | 통과 |
| iOS `KeyManageIOSUITests` (로컬 백엔드) | 통과 |
| 단계별 독립 검증 에이전트 리뷰 | 단계마다 실시, 지적 사항 반영 |
| `verify-tdd-integrity.sh` 보강 사례 14건 (임시 git 저장소) | 고치기 전 11건 불일치, 고친 뒤 14건 일치 |

## 검증하지 못한 것

1. macOS UI 테스트: 이 Mac에서 UI 자동화 모드 승인이 없어 돌지 않았다.
2. 실제 기기의 Face ID·Touch ID 키 저장·해제: 시뮬레이터 UI 테스트는 생체 경로를 지나지 않는다.
3. macOS 직접 조작(저장 패널을 연 채 "즉시" 잠금, 단일 창, 임시 백업 파일 정리).

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| 3단계 UI 테스트 실패를 제품의 잠금 결함으로 진단했으나 테스트 결함(내비게이션 막대 첫 버튼을 누름)이었다 | 사람이 시뮬레이터에서 직접 확인, 검증 단계 | 테스트가 버튼을 식별자로 찾도록 고침 |
| `verify-tdd-integrity.sh`가 다른 테스트 파일에 같은 줄을 더하면 어설션 삭제를 이동으로 봤고, 모듈 정의 파일을 옮겨도 테스트 삭제를 허용했다 | 4단계 독립 검증 | 이름 바꾸기 짝 안에서만 이동을 인정하도록 다시 씀 |
| 같은 스크립트가 R100 이름 바꾸기로 테스트를 `.disabled` 파일로 빼거나 `.disabled`·`XCTSkip`·`it.skip`을 더하는 것을 통과시켰다 | 마무리 독립 검증 | 두 경우를 위반으로 추가 |
| 4단계에서 Apple 화면이 COM-001 복사 직후 안내를 띄우지 않았다 | 4단계 독립 검증 | `:feature`에 `ClipboardCopyNotice`를 두고 iOS·macOS가 그림 |
| SEC-003 가림 화면이 Swift 쪽에만 있었고 정책 감사가 지운 Swift 테스트 폴더를 읽어 조용히 건너뛰었다 | 4단계 독립 검증 | 가림 화면을 Kotlin 상태로 옮기고 감사를 Kotlin 테스트의 값·어설션으로 바꿈 |
| 생체 해제용으로 마스터 비밀번호를 키체인에 저장했다 | 사람 | 유도 키 저장으로 되돌림(ADR-0027) |
