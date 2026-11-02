# KeyManage 개발 가이드라인

## 핵심 아키텍처 및 원칙
- **영지식(Zero-Knowledge) 보안**: 마스터 키 유도 및 암·복호화는 클라이언트 내부에서만 수행하며, 서버는 암호문(Ciphertext)만 보관한다.
- **포트-어댑터(Hexagonal) 멀티 모듈**: 백엔드는 도메인 중심의 4개 모듈(`domain`, `api`, `infrastructure`, `app`)로 관심사를 엄격히 분리한다.
- **의존성 역전(DIP)**: 클라이언트 UI는 멍청한 껍데기(Dumb View)로 유지하며, 백엔드 통신 및 암호화 모듈을 추상화 인터페이스를 통해 주입받는다.

## 작업 시작 전 최신화 및 PR 전 Rebase 강제 (Freshness & Rebase Gate)
- **작업 시작 전 (Pre-Work)**: 모든 코드·문서 작성 전 반드시 `git checkout main && git pull origin main`으로 로컬 `main`을 최신화한 뒤 작업 브랜치를 분기하거나 리베이스한다.
- **커밋 시점 최신성 강제 (`.githooks/pre-commit`)**: 로컬 브랜치가 `origin/main`보다 뒤처져 있으면 커밋이 즉시 차단되므로, `git pull --rebase origin main`으로 항상 최신 상태를 유지한다.
- **PR 및 푸시 전 Rebase 강제 (`.githooks/pre-push`)**: 푸시 전 반드시 `git fetch origin main && git rebase origin/main`을 실행해 `origin/main` 위에 리베이스된 상태를 확보한다. Rebase되지 않은 상태의 푸시는 훅에 의해 차단된다.

## 문서 주도 개발 (Docs-Driven Development)
- **무조건 구현 전 문서 작성이 먼저다.**

### 아키텍처 결정 기록 (ADR)
- 유의미한 설계·기술 결정(대안이 있었고 트레이드오프를 따져 하나를 고른 경우)은 ADR로 남긴다. 시스템 전체 결정은 `docs/adr/`, 한 플랫폼 결정은 그 플랫폼의 `docs/adr/`(`Apps/Web`, `Apps/Apple`, `Back`)에 쓴다. 플랫폼 ADR 번호는 `ADR-WEB-NNN`·`ADR-APL-NNN`·`ADR-BE-NNN`이고, 공통 ADR만 네 자리 순번을 쓴다. 경계 기준은 [docs/writing-guide.md](docs/writing-guide.md)를 따른다.
- ADR은 채택 후 내용을 고치지 않는다. 결정이 바뀌면 새 ADR을 쓰고 옛 ADR은 '대체됨'으로만 표시한다.
- ADR 기록·조회·대체는 `adr-manager:adr-manager` 스킬 절차를 따른다.

### 서비스 정책 SSOT
- 서비스 정책의 유일한 진실 공급원은 `docs/policies/`다.
- 정책의 조회·추가·수정·폐기·점검은 `policy-manager:policy-manager` 스킬 절차를 따른다.
- 되돌리기 쉬운 정책 결정(타이머 값·문구·UI·기본값 등)은 추천 기본값으로 정하고 결과를 보고한다. 되돌리기 비싼 결정(암호 방식·키 유도·저장 데이터 형식·배포 후 API 계약·보안 경계)만 사용자에게 8개 카테고리(경계값, 예외, 사용자 문구 등)를 확인받는다.
- **정책 ↔ 정적 감사 닫힌 루프(Closed-Loop)**: `docs/policies/`의 경계값·예외·표준 문구가 추가·변경되면 `Scripts/check-policy-acceptance.sh`의 불변식 규칙(`*_rules`)을 같은 PR에서 반드시 함께 갱신한다.

### AI 활용 기록
- AI 활용 내용은 `docs/ai-usage/log/`에 항목당 새 파일로 추가한다. 기존 항목 파일은 고치지 않고 `log/README.md` 목록 표에 한 줄을 추가한다. 형식은 `docs/ai-usage/README.md`를 따른다.
- 기록 절차는 `project-docs:ai-usage` 스킬을 따른다.

### 검증 위임 및 정적 게이트
- PR 전 독립 리뷰와 CI·뮤테이션 테스트 규칙은 [ADR-0009](docs/adr/ADR-0009-verification-delegation-pipeline.md) 및 [`.gemini/rules/multi-platform-workflow-gate.md`](.gemini/rules/multi-platform-workflow-gate.md)를 따른다.
- 로컬 Git 훅(`git config core.hooksPath .githooks`)을 활성화해 커밋 시점에 `.githooks/pre-commit`(최신성·문서·TDD 무결성·정책 합격·API 계약·Apple 린트)과 `.githooks/commit-msg`가 자동 실행되고, 푸시 시점에 `.githooks/pre-push`(Rebase 검증)가 실행되도록 유지한다.
- 코드 리뷰에서 반복 적발되는 실수(스텁 잔재, 계층 침범, 동시성 데드락, 테스트 임의 변조, 계약 드리프트)는 프롬프트 지시에만 의존하지 않고 정적 검증(`Scripts/check-swift-architecture.sh`, `Scripts/check-apple-lint.sh`, `Scripts/check-policy-acceptance.sh`, `Scripts/check-api-contract.sh`, `Scripts/verify-tdd-integrity.sh`, ESLint 등) 및 CI 규칙으로 즉시 격상한다.
- 백엔드 도메인 변경 시 `./gradlew check :domain:pitest` 실행 후 `python3 Scripts/mutation-score.py Back/domain/build/reports/pitest/mutations.xml`로 생존 결함(Survived Mutants)을 확인하고, 살아남은 결함의 줄 번호·변이 조건을 타격하는 단위 테스트를 보강한다.

### 문서 검사
- 문서를 고치면 `bash Scripts/check-docs.sh`를 실행해 링크, 정책·ADR·로그 목록 표, 정책·ADR 번호 참조가 맞는지 확인한다. 위반이 있으면 고친 뒤에 커밋한다.
- 루트 `AGENTS.md`(및 호환용 `CLAUDE.md`)는 매 턴 컨텍스트에 포함되므로 작게 유지한다. 세부 도메인 규칙·릴리스 절차·아키텍처 결정은 `docs/README.md` 및 하위 문서에 두고 루트에는 링크만 남긴다.
