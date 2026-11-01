# 크로스 플랫폼 디자인 시스템

- 적용 범위: 모든 클라이언트(Web·데스크톱, iOS, Android)
- 기반: shadcn/ui의 디자인 원칙(컴포넌트 소유, 시멘틱 토큰, 미니멀리즘)과 접근성

> **요약** 모든 클라이언트는 같은 이름과 값의 색상 토큰, 같은 컴포넌트 규격을 쓴다. 각 플랫폼이 토큰과 아이콘을 어떻게 연결하는지는 그 플랫폼 문서에 적는다.

---

## 1. 디자인 원칙

| 원칙 | 내용 |
| :--- | :--- |
| 컴포넌트 소유 | 무거운 외부 UI 라이브러리에 기대지 않는다. 컴포넌트를 각 프로젝트에 복사해 직접 소유한다(shadcn/ui의 Copy, Own & Customize). |
| 보안 신뢰 | 깊은 Slate Navy 배경에 Emerald Green(안전)과 Red(위험) 악센트로 보안 상태를 바로 알린다. |
| 플랫폼 중립 토큰 | 웹의 CSS 변수, iOS의 `UIColor`/`Color`, Android의 `ColorScheme`이 같은 토큰 이름과 값을 쓴다. |

---

## 2. 색상 토큰

모든 플랫폼은 아래 shadcn/ui 표준 이름과 값을 쓴다. 기본 모드는 다크다.

| 토큰 | 라이트 | 다크(기본) | 용도 |
| :--- | :--- | :--- | :--- |
| `--background` | `#F8FAFC` (Slate 50) | `#0F172A` (Slate 900) | 전체 화면 기본 배경 |
| `--foreground` | `#0F172A` (Slate 900) | `#F8FAFC` (Slate 50) | 기본 본문 텍스트 |
| `--card` | `#FFFFFF` (White) | `#1E293B` (Slate 800) | 볼트 카드, 다이얼로그 표면 |
| `--card-foreground` | `#0F172A` (Slate 900) | `#F8FAFC` (Slate 50) | 카드 안 텍스트 |
| `--primary` | `#1E3A5F` (Navy 800) | `#38BDF8` (Sky 400) | 주요 브랜드 액션, 활성 탭 |
| `--primary-foreground` | `#FFFFFF` | `#0F172A` | 주요 버튼 텍스트 |
| `--accent` (Secure) | `#059669` (Emerald 600) | `#10B981` (Emerald 500) | 안전한 비밀번호, 생성·저장 강조 |
| `--accent-foreground` | `#FFFFFF` | `#0F172A` | 악센트 버튼 텍스트 |
| `--muted` | `#F1F5F9` (Slate 100) | `#1E293B` (Slate 800) | 비활성 영역, 칩 배경 |
| `--muted-foreground` | `#64748B` (Slate 500) | `#94A3B8` (Slate 400) | 타임스탬프, 보조 캡션 |
| `--destructive` (Danger) | `#DC2626` (Red 600) | `#EF4444` (Red 500) | 취약 암호 경고, 삭제 액션 |
| `--destructive-foreground` | `#FFFFFF` | `#FFFFFF` | 경고 버튼 텍스트 |
| `--border` | `#E2E8F0` (Slate 200) | `rgba(255, 255, 255, 0.08)` | 카드와 입력창 테두리 |
| `--input` | `#E2E8F0` (Slate 200) | `#334155` (Slate 700) | 입력 필드 외곽선 |
| `--ring` | `#38BDF8` (Sky 400) | `#38BDF8` (Sky 400) | 포커스 링 |

---

## 3. 플랫폼 바인딩

각 플랫폼이 2장의 토큰을 코드에 연결하는 방법은 그 플랫폼의 `docs/platform-bindings.md`에 적는다.

---

## 4. 타이포그래피

| 용도 | 규칙 |
| :--- | :--- |
| UI 레이블·헤더 | 기하학적 산세리프 계열 |
| 비밀번호 텍스트 | **모노스페이스(고정폭) 글꼴을 반드시 쓴다.** 대문자 `I`, 소문자 `l`, 숫자 `1`, 특수문자 `\|`처럼 헷갈리는 글자를 구분하기 위해서다. |

플랫폼별 글꼴 이름은 각 플랫폼의 `docs/platform-bindings.md`에 적는다.

---

## 5. 핵심 컴포넌트

각 플랫폼 프로젝트에 직접 복사해 소유하는 컴포넌트 규격이다. 괄호 안은 규격상 이름이다. 각 플랫폼의 실제 파일·타입 이름은 다를 수 있다.

### 5.1. `Button` (`KMButton`)

암호 생성, 볼트 저장, 클립보드 복사 같은 주요 액션을 실행한다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `variant` | `primary` \| `outline` \| `destructive` \| `ghost` | | `primary`: 솔리드 스카이, `outline`: 테두리와 투명 배경, `destructive`: 레드, `ghost`: 아이콘 전용 투명 버튼 |
| `size` | `sm` \| `md` \| `lg` | `md` | 높이 `sm` 32pt, `md` 40pt, `lg` 48pt |
| `title` | String | | 버튼 텍스트 |
| `icon` | 아이콘 (선택) | | 왼쪽·오른쪽 아이콘 |
| `isLoading` | Boolean | false | 로딩 스피너 표시 |
| `isDisabled` | Boolean | false | 비활성 상태 |
| `action` | `() -> Void` | | 탭·클릭 콜백 |

### 5.2. `Card` (`KMCard`)

계정 자격증명 항목과 섹션을 담는다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `padding` | `sm` \| `md` \| `lg` | `md` | `sm` 12pt, `md` 16pt, `lg` 20pt |
| `isInteractive` | Boolean | false | 탭할 수 있는 목록 항목인지. 켜면 hover·press 애니메이션을 쓴다. |
| `content` | 뷰 슬롯 | | 안에 넣을 뷰 |

### 5.3. `PasswordInputField` (`KMPasswordField`)

비밀번호 입력, 가림·평문 전환, 복사를 한 필드에서 한다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `text` | String | | 입력값 바인딩 |
| `placeholder` | String | "비밀번호 입력" | 플레이스홀더 |
| `isSecured` | Boolean | true | 가림 여부(`••••••••` 표시) |
| `showCopyAction` | Boolean | true | 오른쪽 복사 버튼 표시 |
| `onCopy` | `() -> Void` | | 복사 버튼을 눌렀을 때 부르는 콜백 |
| `onTextChange` | `(String) -> Void` | | 텍스트 변경 콜백 |

1. 실제 클립보드 쓰기와 30초 뒤 비우기(COM-001)는 컴포넌트가 아니라 `onCopy`를 받은 쪽이 맡는다.
2. 평문으로 바꾼 뒤 10초 동안 조작이 없으면, 컴포넌트는 보안을 위해 `isSecured = true`로 돌아간다.

### 5.4. `EntropyGauge` (`KMGaugeBar`)

비밀번호 강도(0~100점)를 실시간 진행 막대로 보여 준다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `score` | Int (0~100) | | 엔트로피 점수 |
| `showLabel` | Boolean | true | 상태 텍스트("취약함" / "보통" / "초강력") 표시 |
| `animated` | Boolean | true | 점수가 바뀔 때 게이지가 부드럽게 차오르는 애니메이션 |

| 점수 | 색상 |
| :--- | :--- |
| 0~59점 | `--destructive`(Red) |
| 60~79점 | `--primary`(Sky) |
| 80~100점 | `--accent`(Emerald) |

### 5.5. `BurnTimerBadge` (`KMBurnBadge`)

클립보드 복사 뒤 30초 카운트다운과 자동 비우기 상태를 보여 준다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `remainingSeconds` | Int (30 → 0) | | 남은 시간 |
| `totalSeconds` | Int | 30 | 전체 타이머 시간 |
| `onBurned` | `() -> Void` | | 0초가 되어 비우기가 끝났을 때 콜백 |

표시 형태: `"30s"` 원형 축소 배지 또는 화면 위쪽 1px 진행 막대

---

## 6. 아이콘

모든 플랫폼은 아래 용도마다 같은 의미의 아이콘을 쓴다. 플랫폼별 아이콘 이름은 각 플랫폼의 `docs/platform-bindings.md`에 적는다.

| 용도 | 아이콘 의미 |
| :--- | :--- |
| 비밀번호 보기 | 눈 |
| 비밀번호 숨김 | 사선이 그어진 눈 |
| 클립보드 복사 | 겹친 문서 |
| 복사 완료 | 원 안의 체크 |
| 암호 재생성 | 시계 방향 화살표 |
| 볼트 잠금 | 자물쇠 |
| 경고·취약 | 삼각형 느낌표 |
| 오프라인 배너 | 사선이 그어진 와이파이 |
