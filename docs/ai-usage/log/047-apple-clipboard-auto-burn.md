# 2026-11-01 · Apple 클립보드 복사 및 30초 자동 소각 엔진 구현 (COM-001)

- 이슈·PR: COM-001 (#40)
- 커밋: 1bd0919

> **요약** Apple 클라이언트(iOS·macOS)에 클립보드 복사 및 30초 자동 소각 도메인 엔진(COM-001)을 구현했다. 사용자가 볼트 값을 복사하면 30초 뒤 클립보드를 비우고, 30초 이전 재복사 시 기존 타이머를 취소하며, 앱 잠금(SEC-003) 시 즉시 비우도록 설계했다. 독립 코드 리뷰어가 적발한 deinit 소각 누락(P1) 및 락 내 외래 메서드 호출(P1) 결함을 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild
- 플러그인·스킬: `code-review`, `clean-architecture-layers`

## 요청

사용자가 "권장 부터 해 계획 말해 봐", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가"를 지시하여 COM-001 클립보드 자동 소각 구현에 착수했다.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Clipboard/ClipboardManager.swift`: 클립보드 복사 및 30초 자동 소각 매니저(`ClipboardManager`), 포트 인터페이스(`PasteboardProtocol`), 타이머 스케줄러(`ClipboardTimerScheduler`, `DefaultClipboardTimerScheduler`), 표준 문구 및 에러.
- `Apps/Apple/Projects/Core/Domain/Tests/ClipboardManagerTests.swift`: COM-001 단위 테스트 15개 (30초 자동 소각, 재복사 타이머 무효화, 잠금 즉시 소각, deinit 보안 소각, GCD 스케줄러 검증, 다중 스레드 동시성 스트레스 테스트).
- `Scripts/check-policy-acceptance.sh`: COM-001 정책 합격 기준 6종 기계적 감사 항목 추가.

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | 테스트 작성(RED)과 코드 리뷰(REVIEW)만 서브에이전트에 위임하고, 구현(GREEN) 및 통합 검증은 메인 에이전트가 단일 직접 수행하는 워크플로우 채택 |
| 채택 | Domain 계층 내 UIKit/AppKit 등 UI 프레임워크 종속성을 배제하고 `PasteboardProtocol` 추상화 포트로 완전 격리 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh` | 통과 (위반 0건) |
| `bash Scripts/check-policy-acceptance.sh` | 통과 (누락 0건) |
| `bash Scripts/verify-tdd-integrity.sh` | 통과 (TDD 불변성 100%) |
| `bash Scripts/verify-apple.sh` | 통과 (모든 빌드 및 단위 테스트 117개 All Green) |

## 검증하지 못한 것

1. 실제 물리 기기 환경에서 다른 서드파티 앱과의 클립보드 공유(유니버설 클립보드 등) 상호작용 (macOS 호스트 및 가상 스케줄러 환경에서 단위 테스트로 검증함)

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `ClipboardManager.deinit` 시 `pasteboard.clear()` 미호출로 인해 인스턴스 해제 시 클립보드에 비밀번호가 영구 방치되는 보안 결함 (P1) | 독립 코드 리뷰어 서브에이전트 | 소멸자(`deinit`)에서 `_cancelTimer?()` 및 `try? pasteboard.clear()`를 명시적으로 실행하도록 수정 |
| `NSLock` 임계 영역 내부에서 외래 메서드(`pasteboard.setString`, `scheduler.schedule`)를 호출하여 데드락 발생 위험 (P1) | 독립 코드 리뷰어 서브에이전트 | 락 내부에서는 상태 갱신만 수행하고, 외부 I/O 및 외래 스케줄러 메서드는 락 해제 후 안전하게 호출하도록 리팩토링 |
| `ClipboardStrings` 내 `clearFailedMessage` 상수가 누락되어 에러 설명에 인라인 하드코딩된 결함 (P5) | 독립 코드 리뷰어 서브에이전트 | `ClipboardStrings.clearFailedMessage` 상수를 추가하고 SSOT 일원화 |
