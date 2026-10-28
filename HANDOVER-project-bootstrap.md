# [인수인계 문서] `project-bootstrap` 스킬 기획 및 구현 가이드

이 문서는 `carti-marketplace` 프로젝트에서 새롭게 제작할 **`project-bootstrap` (프로젝트 킥오프 및 시니어 시스템 설계 오케스트레이터)** 스킬의 배경, 철학, 5단계 워크플로우, 프롬프트 구조, 파일 배치를 온전히 인수인계하기 위해 작성되었습니다.

---

## 1. 스킬 개요 및 기획 의도

* **스킬 이름**: `project-bootstrap` (슬래시 커맨드: `/project-bootstrap`)
* **한 줄 정의**: 새 프로젝트를 맨땅(쌩배)에서 시작할 때, 주니어처럼 코드부터 치는 본능을 제어하고 **시니어 엔지니어의 시스템 설계 5단계(요구사항 → 계약 → 아키텍처 격리 → ADR → 스캐폴딩)**를 대화형으로 강제·유도하는 오케스트레이터 스킬.
* **해결하는 핵심 문제**:
  * 대부분의 바이브 코딩이나 신규 프로젝트는 시작하자마자 `create-next-app`이나 `tuist init`부터 치고 코드를 짜다가, 데이터 스키마 충돌, 플랫폼 간 강결합, 백엔드 교체 불가능, 정책 부재로 프로젝트를 뒤엎게 됨.
  * `carti-marketplace`에 이미 존재하는 `adr-manager`, `policy-manager`, `project-docs`, `ui-ux-pro-max` 등 훌륭한 도구들을 **어떤 순서로 조합해야 하는지 모르는 문제**를 해결하는 **마스터 진입점** 역할 수행.
* **설계 레퍼런스**:
  * GeekNews [시니어 엔지니어처럼 소프트웨어 시스템 설계하기 (5단계 접근법)](https://news.hada.io/topic?id=35613)
  * `KeyManage` 프로젝트 실전 검증 사례 (무결성 검사 100% 통과한 거버넌스 수립)

---

## 2. 5단계 대화형 워크플로우 (Interactive Workflow)

에이전트는 `/project-bootstrap` 명령을 받으면 코드를 작성하지 않고, 아래 5개 단계를 순차적으로 유도합니다:

```
[ /project-bootstrap 실행 ]
        │
        ├─► Phase 1. 문제 정의 & 비목표(Non-Goals) 인터뷰
        │
        ├─► Phase 2. 문서 거버넌스 뼈대 설치 (project-docs + adr + policies 연동)
        │
        ├─► Phase 3. 3대 인터페이스 계약(Contracts) 선행 확정
        │
        ├─► Phase 4. 초기 핵심 기술 결정 ADR 채택 (대안 2개 비교 강제)
        │
        └─► Phase 5. 물리적 경계 격리 및 첫 구현 모듈 착수
```

---

### Phase 1: 문제 정의 & 비목표(Non-Goals) 인터뷰
* **목적**: 프로젝트의 본질을 좁히고, 오버엔지니어링을 방지하기 위해 "만들지 않을 것"을 먼저 확정.
* **에이전트 질문**:
  1. "이 프로젝트가 해결하려는 가장 핵심적인 한 가지 문제는 무엇인가요?"
  2. "이번 구현에서 **절대 만들지 않을 것(Non-Goals)**은 무엇인가요? (예: 과도한 RDBMS, 불필요한 결제 모듈 등)"
  3. "이 프로덕트가 성공했다고 판단할 단 하나의 핵심 동작 순간은 어떤 모습인가요?"

---

### Phase 2: 문서 거버넌스 뼈대 설치 (Bootstrap Integration)
* **목적**: 구현 전 문서 체계(ADR, Policy SSOT, AI-Log)를 원클릭으로 구축.
* **수행 동작**:
  1. `docs/README.md` (문서 지도) 생성.
  2. `adr-manager:bootstrap` 연동 (`docs/adr/README.md`).
  3. `policy-manager:bootstrap` 연동 (`docs/policies/` 영역 분할 및 `README.md`, `CHANGELOG.md`).
  4. `project-docs:bootstrap` 연동 (`docs/ai-usage/`, `Scripts/check-docs.sh`, `CLAUDE.md`).
  5. `bash Scripts/check-docs.sh` 실행으로 초기 문서 무결성 검증.

---

### Phase 3: 3대 인터페이스 계약(Contracts) 선행 확정
* **목적**: 백엔드와 멀티 플랫폼 클라이언트가 서로 블로킹되지 않고 100% 병렬 개발할 수 있도록 데이터 계약 고정.
* **필수 생성 문서 3종**:
  1. **데이터/암호화/저장소 규격서 (`docs/data-spec.md` 또는 `vault-spec.md`)**:
     * 바이트 단위 파라미터, 인코딩 표준, 평문 직렬화 JSON 스키마.
  2. **백엔드 REST/gRPC API 계약서 (`docs/api-spec.md`)**:
     * 엔드포인트 URL, Request/Response DTO, 표준 에러 응답 포맷 (`code`, `message`).
     * 클라이언트가 백엔드 없이 `Mock`으로 바로 개발할 수 있는 기준선 마련.
  3. **shadcn/ui 기반 시멘틱 디자인 시스템 (`docs/design-system.md`)**:
     * `ui-ux-pro-max-skill`을 호출해 도메인에 맞는 Slate/Navy/Emerald 컬러 토큰 및 타이포그래피 추출.
     * Web(CSS 변수), iOS(UIColor/Color 듀얼), Android(ColorScheme) 1:1 매핑표 및 5대 필수 컴포넌트 카탈로그.

---

### Phase 4: 초기 핵심 기술 결정 ADR 채택 (Trade-offs & ADR)
* **목적**: "왜 그 기술을 썼는가?"에 대한 정당성과 트레이드오프를 박제.
* **규칙**: **대안 2개 이상 비교 및 기각 사유가 없으면 ADR 생성을 반려함.**
* **유도 항목 (최소 3건)**:
  1. `ADR-0001`: 시스템 보안/아키텍처 모델 (예: 중앙 집중형 vs 영지식 클라이언트 분산)
  2. `ADR-0002`: 백엔드/인프라 기술 스택 (예: 서버리스 Next.js vs Kotlin/Spring Boot 멀티모듈)
  3. `ADR-0003`: 영속성/저장소 어댑터 (예: 무거운 RDBMS vs 경량 파일/인메모리 어댑터)

---

### Phase 5: 물리적 경계 격리 및 첫 구현 착수
* **목적**: 다른 영역의 변경이 다른 영역으로 절대 번지지 않는 무의존(Zero-Coupling) 구조 확립.
* **디렉터리 강제**:
  * `Back/`: 백엔드 독자 모듈 (헥사고날 구조: `:domain`, `:api`, `:infrastructure`, `:app`)
  * `Apps/iOS/`, `Apps/Android/`, `Apps/Web/`: 플랫폼별 완전 격리 프로젝트
* **DIP(의존성 역전) 규칙 확립**:
  * 클라이언트는 네트워크 구현체가 아닌 `ServiceProtocol`에만 의존하도록 가이드.
  * 백엔드 없이도 즉시 실행 가능한 `MockAdapter` 작성을 1차 마일스톤으로 설정.

---

## 3. `carti-marketplace` 내 추천 파일 배치 구조

사용자가 `carti-marketplace` 레포에서 작업할 때 아래와 같이 플러그인 또는 스킬로 배치할 수 있습니다:

```text
carti-marketplace/
├── plugins/
│   └── project-bootstrap/                      # 신규 플러그인
│       ├── plugin.json                         # 플러그인 매니페스트
│       └── skills/
│           └── project-bootstrap/
│               ├── SKILL.md                    # 5단계 워크플로우 지침 본문
│               └── references/
│                   ├── interview-questions.md  # Phase 1 인터뷰 질문지
│                   ├── contracts-template.md   # Phase 3 계약서 템플릿
│                   └── isolation-checklist.md  # Phase 5 경계 격리 점검표
```

### `SKILL.md` 메타데이터 예시
```yaml
---
name: project-bootstrap
description: 새 프로젝트를 시작할 때 시니어 시스템 설계 5단계를 밟으며 문서 거버넌스(ADR, 정책), 데이터 계약(Contracts), 디자인 시스템, 경계 격리를 선행 구축하도록 유도하는 마스터 온보딩 스킬. "프로젝트 시작하자", "새 프로젝트 세팅해줘", "쌩배로 시작할래" 요청에 사용.
---
```

---

## 4. KeyManage 실전 검증 성공 사례 (Case Study)

`KeyManage`에서 실제로 이 5단계를 밟아 거둔 성과:
1. **문서 무결성**: `Scripts/check-docs.sh` 자동 검사 100% 통과 (ADR 3건, 정책 4건, AI 로그 5건).
2. **병렬 개발 준비 완료**: `vault-spec.md`, `api-spec.md`, `design-system.md`가 선행 확정되어, 백엔드와 3개 플랫폼 클라이언트가 서로 기다리지 않고 Mock 기반 동시 개발 가능.
3. **완전한 결합도 제거**: 백엔드를 Spring Boot에서 Supabase나 로컬 파일로 통째로 갈아치워도 클라이언트 UI 코드는 단 1줄도 수정할 필요가 없는 DIP 구조 달성.
