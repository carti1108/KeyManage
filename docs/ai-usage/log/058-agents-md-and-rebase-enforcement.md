# 2026-11-02 · AGENTS.md 범용화 및 작업 전 최신화·PR 전 Rebase 강제 게이트 구축

- 이슈·PR: 자체 개선
- 커밋: 2c47a13

> **요약** Claude Code 전용이던 `CLAUDE.md`를 범용 에이전트 표준인 `AGENTS.md`로 전환하고(하위 호환 유지), 에이전트가 작업 전 `main` 브랜치를 pull 최신화하고 PR 전 `origin/main` 위에 rebase하도록 `.githooks/pre-commit` 및 `.githooks/pre-push`를 통해 기계적으로 강제했다.

## 사용 도구

- 도구: Antigravity (Gemini 3.8 Flash)
- 플러그인·스킬: `project-docs:ai-usage`, `issue-worktree:commit-rules`

## 요청

- "머지했고 지금 CLAUDE.md 파일을 AGENT.md 로 이름을 바꿀 수 있나 클로드만 사용하는 md가 아니고 다른 ai tool들도 범용적으로 쓸 수 있게"
- "그리고 항상 작업(코드 생성, 수정 등등등)하기 전에는 main 브랜치 pull받아서 최신화 하고, pr전에도 최신화하고 작업 브랜치 main에 rebase 하도록 강제할 수 있나"

## AI 산출물

- **AGENTS.md 표준 전환 및 하위 호환 (`CLAUDE.md` → `AGENTS.md`)**:
  - `AGENTS.md`: 범용 AI 툴(Codex, Antigravity, Cursor, Windsurf 등) 표준 지침 파일로 전환
  - `CLAUDE.md`: Claude Code의 import 문법(`@AGENTS.md`)을 적용해 단일 진실 공급원(SSOT) 유지
  - `Scripts/check-docs.sh`: `README.md`, `CLAUDE.md`, `AGENTS.md`, `AGENT.md`를 모두 정식 문서 검사 스코프로 지원
- **작업 전 최신화 및 PR 전 Rebase 기계적 강제**:
  - `.githooks/pre-commit`: 작업 브랜치 커밋 시 `origin/main` 대비 뒤처짐(Behind)이 감지되면 커밋을 자동 차단 (`git pull --rebase origin main` 강제)
  - `.githooks/pre-push`: 푸시 시 현재 브랜치의 merge-base가 최신 `origin/main`과 일치하지 않으면 푸시를 자동 차단 (`git rebase origin/main` 강제)
  - `AGENTS.md` & `.gemini/rules/multi-platform-workflow-gate.md`: 작업 시작 전 `main` 최신화 및 PR 전 rebase 의무 규칙 명문화

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | 범용 AI 도구 호환을 위해 `CLAUDE.md`를 `AGENTS.md`로 전환하고, 훅 기반으로 rebase를 기계적 강제하기로 결정 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `Scripts/check-docs.sh` (문서 검사) | 통과 |
| `Scripts/check-apple-lint.sh` & `check-swift-architecture.sh` | 통과 |
| `Scripts/check-policy-acceptance.sh` & `check-api-contract.sh` | 통과 |
| `Scripts/verify-tdd-integrity.sh` | 통과 |

## 검증하지 못한 것

- 없음.

## AI의 오류와 정정

- 없음.
