# 2026-10-27 · 초기 아키텍처 설계 및 거버넌스 문서 체계 구축 (소급 기록)

> 요약: `HANDOVER.md`를 바탕으로 브레인스토밍을 진행하여 영지식 보안 모델과 Kotlin/Spring Boot 포트-어댑터 아키텍처를 확정하고, 구현 전 문서 체계(ADR 3건, 정책 SSOT 3건, AI 로그 체계)를 구축했다.

## 사용 도구

- 도구: Antigravity IDE (Gemini 3.8 Flash High)
- 플러그인/스킬: `superpowers:brainstorming`, `adr-manager`, `policy-manager`, `project-docs:ai-usage`

## 요청

- `HANDOVER.md` 내용을 확인하고 대화하며 프로젝트 구조를 재설계할 것을 요청.
- 추상화, 관심사 분리, 모듈화, 백엔드와 클라이언트 간의 의존성 역전(DIP)을 핵심 가치로 제시.
- `MusicSearch/Back`의 멀티모듈 레퍼런스를 검토하고, 코드 작성 전 반드시 문서 작성을 선행하도록 요구.

## AI 산출물

- 영지식(Zero-Knowledge) 보안 모델 및 클라이언트-백엔드 책임 경계 정의.
- 포트-어댑터 4개 모듈(`domain`, `api`, `infrastructure`, `app`) 기반의 시스템 아키텍처 다이어그램 및 계획서 제시.
- 문서 체계 뼈대 구축: `docs/README.md`, `docs/adr/`, `docs/policies/`, `docs/ai-usage/`, `Scripts/check-docs.sh`.
- 아키텍처 결정 기록 3건(`ADR-0001`, `ADR-0002`, `ADR-0003`) 및 서비스 정책 3건(`SEC-001`, `GEN-001`, `COM-001`) 작성.

## 사람의 판단

- **보안 모델 채택**: 서버 중앙 암호화 대신 클라이언트 단독 암·복호화(Zero-Knowledge) 방식을 선택.
- **백엔드 기술 스택 전환**: AI가 무료 호스팅(Vercel) 편의성으로 제안한 TypeScript 서버리스 방식을 기각하고, `MusicSearch` 프로젝트를 레퍼런스로 제시하며 Kotlin/Spring Boot 포트-어댑터 구조로 강력히 확정.
- **배포 및 DB 단순화**: 복잡한 클라우드 결제나 RDBMS 구축 대신, 로컬 Docker/터널링 및 단일 파일 기반 볼트 저장소 어댑터로 진행할 것을 승인.
- **문서 주도 개발 강제**: 코드 구현 착수 전 문서(ADR, Policy, AI-Log)를 반드시 먼저 작성하도록 지시.

## 검증

- `MusicSearch/Back` 멀티모듈 빌드 설정 및 Docker 설정 분석 완료.
- `Scripts/check-docs.sh` 실행을 통한 문서 링크 및 정합성 검증 예정.

## 검증하지 못한 것

1. 백엔드 Kotlin 프로젝트의 실제 Gradle 컴파일 및 런타임 구동 검증 (문서 구축 단계이므로 구현 전).

## AI의 오류와 정정

1. **플러그인 설치 오해**: 사용자가 처음에 `carti-marketplace` 추가를 요청했을 때, 세부 의도를 바로 파악하지 못하고 복잡한 시스템 내부 설정을 탐색하다가 사용자의 직접적인 피드백("그냥 마켓플레이스 플러그인 설치하라고")을 받고서야 프로젝트 스코프 플러그인 설치(`claude plugin install`)를 완료함.
2. **기술 스택 편향**: 5일 과제의 일정과 Vercel 무료 배포에 지나치게 매몰되어 TypeScript 백엔드를 유도하려 했으나, 사용자의 멀티모듈/관심사 분리 의도와 `MusicSearch` 레퍼런스 검토를 통해 Kotlin/Spring Boot 구조의 정당성을 확인하고 바로잡음.

## 근거 링크

- 없음(커밋 전)
