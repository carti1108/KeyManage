# 2026-11-18 · Android 구현 계획과 아키텍처 ADR 작성

- 이슈·PR: #3, #128
- 커밋: 없음(커밋 전)

> **요약** 기존 코드, 디자인 시스템, DS Builder 목업을 조사해 Android 클라이언트(이슈 #3) 구현 계획을 세우고 ADR-AND-001로 아키텍처를 기록했다. 사용자가 단독 모듈, Clean Architecture·RIBs·마이크로 피처, ViewState/ViewAction 패턴, uber/RIBs를 정했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), GitHub CLI, 내장 브라우저
- 플러그인·스킬: `docs:adr-manager`, Explore 서브에이전트(코드베이스 조사)

## 요청

Android 이슈를 확인하고, 기존 코드·디자인 시스템·Figma 화면을 파악해 구현 계획을 세운 뒤 1단계(ADR과 플랫폼 바인딩)를 진행하는 요청이었다.

## AI 산출물

- 8단계 Android 구현 계획(문서 → 스캐폴딩·CI → 암호 엔진 → 토큰 → 컴포넌트 → 데이터·동기화 → 화면 → 교차 검증)
- `Apps/Android/docs/adr/ADR-AND-001-android-architecture.md`와 Android ADR 목록 README
- `platform-bindings.md`에 HKDF, 비밀번호 전처리, 난수 바인딩 추가
- 공통 ADR 규칙·템플릿·`AGENTS.md`에 `ADR-AND-NNN` 접두어 등록, 예전 스킬 이름을 `docs:*`로 정리
- 이슈 #128(Apple Domain 계층의 인프라 구현 분리) 등록

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | KMP 공유 대신 Android 단독 Gradle 모듈 |
| 지시 | Clean Architecture, RIBs, 마이크로 피처 구조와 ViewState/ViewAction 패턴 |
| 선택 | RIBs 엔진은 MicroRIBs 포팅 대신 uber/RIBs |
| 수정 | Figma MCP 대신 `Tools/figma-builder/code.js`를 시안 기준으로 확인 |
| 지시 | Apple Domain 계층 문제를 이슈로 등록 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| uber/RIBs 저장소 확인(`gh api`) | v0.16.6, `rib-android-compose`·`rib-coroutines` 존재 확인 |

## 검증하지 못한 것

1. ADR의 모듈 구성과 uber/RIBs 조합은 아직 빌드로 확인하지 않았다. 2단계 스캐폴딩에서 확인한다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| Apple Domain의 `Security` import를 Keychain 사용으로 설명함 | 이슈 작성 중 AI가 코드를 다시 확인 | `SecRandomCopyBytes` 난수용으로 정정해 이슈에 기록 |
| uber/RIBs를 "Compose 연동이 약하고 RxJava 중심"이라고 설명함 | 사용자 선택 후 AI가 저장소를 확인 | Compose·코루틴 모듈이 있음을 알리고 ADR에 반영 |
| 항목 상세의 클립보드 배지를 막대로 해석함 | `code.js` 확인 중 AI가 발견 | BurnTimerBadge로 정정 |
