# 2026-11-02 · Web 와이어프레임(03~06) 기반 UI 레이아웃 정렬 및 컴포넌트 구조화

- 이슈·PR: -
- 커밋: -

> **요약** 와이어프레임 명세(`Tools/wireframe-builder`의 Web 1440×900 3분할)에 맞춰 Web UI 레이아웃을 기존 단일 컬럼 카드에서 좌측 사이드바(240px: 볼트·생성기·설정)와 데스크톱 2분할(360px 목록 + flex 상세/편집) 구조로 개편했다. DIP 및 포트-어댑터 아키텍처에 따라 도메인 계층(암복호화, 볼트 동기화, 세션 등)과 분리된 UI 프레젠테이션 계층만 수정하여 비즈니스 로직 영향을 완전히 배제했으며, 독립 `SettingsView` 및 `PasswordGenerator` 독립 화면을 연동하고 기존 639개 테스트 전수 통과를 유지했다.

## 사용 도구

- 도구: Antigravity (Gemini), Vitest, Vite, TypeScript
- 플러그인·스킬: `clean-architecture-layers`, `commit-rules`, `ai-usage`

## 요청

사용자가 "와이어프레임대로 ui만 수정해 봐, 내부 비즈니스 로직하고 ui랑 따로 작업해도 문제 없도록 설계되어 있지?"라고 요청했다. 아키텍처적 무결성을 확인하고 와이어프레임 명세에 맞춰 UI 컴포넌트와 레이아웃을 정렬했다.

## AI 산출물

- `Apps/Web/src/ui/screens/VaultHomeScreen.tsx`:
  - 와이어프레임 Web 240px 사이드바 도입: `KeyManage` 로고, [볼트, 생성기, 설정] 3대 탭 내비게이션, 사용자 이메일, 자동 잠금/백업 푸터, 잠그기/로그아웃 버튼 배치
  - 탭 상태에 따른 화면 렌더링 분기 (볼트: `children`, 생성기: `PasswordGenerator` 독립 카드, 설정: `SettingsView`)
- `Apps/Web/src/ui/screens/SettingsView.tsx` (신규):
  - 와이어프레임 06 설정 화면 컴포넌트 신설: 계정(이메일, 동기화 상태), 보안(클립보드 30초 고정 안내, 마스터 키 영지식 안내), 서버 주소, 세션 제어(잠그기, 로그아웃) 구조화
- `Apps/Web/src/ui/vault/VaultScreen.tsx`:
  - `layout?: 'single' | 'split'` 옵션 지원
  - 데스크톱(`lg:`) 2분할 스플릿 뷰(좌측 360px `ItemList`, 우측 flex `ItemDetail` / `ItemForm` / 빈 상태 안내) 및 모바일 단일 스택 전환 지원
- `Apps/Web/src/ui/vault/PasswordGenerator.tsx`:
  - `onClose`, `onUse`, `onCopy` 선택적 바인딩 지원으로 05 생성기 독립 화면과 04 항목 편집 폼 내 인라인 도구 양쪽에서 유연하게 재사용 가능하도록 개선
- `Apps/Web/src/ui/session/SessionRoot.tsx`:
  - `VaultHomeScreen` 사이드바 푸터에 `AutoLockSelect`, `BackupActions` 주입 및 데스크톱 스플릿 뷰 연동

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | UI 계층과 도메인 비즈니스 계층 간 완전한 DIP 분리 유지 확인 채택 |
| 채택 | 와이어프레임 3분할 규격(사이드바 240px + 목록 360px + 상세 flex) 및 3개 탭(볼트/생성기/설정) 적용 채택 |
| 채택 | 기존 단위 테스트 호환성 유지를 위한 `layout="single"`/`"split"` 듀얼 모드 설계 채택 |

## 주요 대화 내용

- **사용자**: 와이어프레임과 현재 웹 UI 비교 요청 후, "와이어프레임대로 ui만 수정해 봐, 내부 비즈니스 로직하고 ui랑 따로 작업해도 문제 없도록 설계되어 있지?" 지시
- **어시스턴트**:
  - 도메인 로직(`src/domain/`)과 Dumb View UI(`src/ui/`)가 포트와 모델로 완벽 분리되어 있어 안전함을 확인
  - 와이어프레임(`Tools/wireframe-builder/code.js`)의 Web 3컬럼 규격 상세 파악
  - `VaultHomeScreen`, `VaultScreen`, `PasswordGenerator`, `SettingsView` 개편 및 신설
  - Web 전체 51개 테스트 파일 639개 단위 테스트 100% 통과 및 `vite build` 빌드 성공 확인

## 다음 작업

- PR 리뷰 요청 및 디자인 시스템(색상·간격 세부 스타일링) 고도화 검토
