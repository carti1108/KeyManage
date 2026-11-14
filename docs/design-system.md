# 크로스 플랫폼 디자인 시스템

- 적용 범위: 모든 클라이언트(Web·데스크톱, iOS·macOS, Android)
- 기반: shadcn/ui의 디자인 원칙(컴포넌트 소유, 시멘틱 토큰, 미니멀리즘)과 접근성, 각 OS의 디자인 언어
- 결정 근거: [ADR-0025](adr/ADR-0025-design-system-shared-contract-platform-expression.md)

> **요약** 모든 클라이언트는 같은 **토큰 이름**과 같은 **컴포넌트 계약**을 쓴다. 토큰 값, 재질, 모양, 모션, 네이티브 컨트롤은 각 플랫폼의 디자인 언어를 따른다. 각 플랫폼이 토큰과 아이콘을 어떻게 연결하는지는 그 플랫폼 문서에 적는다.

---

## 1. 디자인 원칙

### 1.1. 네 개의 층

| 층 | 공통 여부 | 정하는 것 |
| :--- | :--- | :--- |
| 브랜드·의미층 | 공통 | 시멘틱 토큰 이름과 의미(2·3장) |
| 컴포넌트 계약층 | 공통 | 컴포넌트 이름, 역할, 변형, 상태, 접근성, 사용자 문구(6장) |
| 표현층 | 플랫폼별 | 재질, 모양, 모서리, 그림자, 모션, 글꼴(5장) |
| 네이티브 컨트롤층 | 플랫폼별 | 시스템 컴포넌트를 쓸지, 직접 만들지(5장) |

공통층을 바꾸려면 이 문서를 고친다. 표현층은 각 플랫폼의 `docs/platform-bindings.md`에서 정한다. 단, 이 문서의 공통 규칙을 어길 수는 없다.

### 1.2. 지켜야 할 원칙

| 원칙 | 내용 |
| :--- | :--- |
| 컴포넌트 소유 | 무거운 외부 UI 라이브러리에 기대지 않는다. 컴포넌트를 각 프로젝트에 두고 직접 소유한다. 단, OS가 주는 시스템 컨트롤은 감싸서 쓴다. |
| 흑백과 두 가지 신호색 | 바탕·표면·글자·주요 액션은 블랙과 화이트(무채색)로만 쓴다. 유채색은 안전(Emerald)과 위험(Red) 두 가지뿐이며, 이 의미와 색상 계열은 어느 플랫폼에서도 바꾸지 않는다. |
| 이름은 공통, 값은 플랫폼 | 웹의 CSS 변수, Apple의 `Color`, Android의 `ColorScheme`은 같은 토큰 이름을 쓴다. 값은 2장의 브랜드 기준값을 기본으로 하되, 플랫폼 재질 위에서 대비를 맞추기 위해 조정할 수 있다. |
| 리터럴 금지 | Feature·화면 코드에는 색상값, 간격·모서리 숫자, 기본 글자 크기를 직접 쓰지 않고 토큰을 쓴다. Apple은 `Scripts/check-apple-lint.sh` 규칙 6·7·8이 `Features/**`를, 웹은 `Apps/Web/scripts/designLiterals.ts`(`npm run check`)가 척도 밖 간격, 기본 팔레트 색, 기본 글자 크기, 간격·모서리·색 임의 값을 막는다. |

---

## 2. 색상 토큰

모든 플랫폼은 아래 shadcn/ui 표준 이름을 쓴다. 팔레트는 무채색(Neutral) 단계와 Emerald·Red 신호색으로만 이뤄진다. 라이트·다크 **브랜드 기준값**은 [`tokens.json`](../Tools/design-tokens/tokens.json) 한 곳에만 적고, 플랫폼 코드는 여기서 생성한다([ADR-0026](adr/ADR-0026-design-token-generation.md)). 웹과 데스크톱은 이 값을 그대로 쓰고, 다른 플랫폼은 1.2의 규칙 안에서 조정할 수 있다. 기본 모드는 다크다.

| 토큰 | 의미 | 플랫폼 조정 |
| :--- | :--- | :--- |
| `--background` | 전체 화면 기본 배경 | 허용(시스템 배경 사용 가능) |
| `--foreground` | 기본 본문 텍스트 | 허용(시스템 레이블 색 사용 가능) |
| `--card` | 볼트 카드, 다이얼로그 표면 | 허용(시스템 재질 사용 가능) |
| `--card-foreground` | 카드 안 텍스트 | 허용 |
| `--primary` | 주요 액션, 활성 탭, 강도 '보통' | **무채색(블랙·화이트) 고정** |
| `--primary-foreground` | 주요 버튼 텍스트 | 허용 |
| `--accent` (Secure) | 안전, 생성·저장 강조 | **Emerald 계열 고정** |
| `--accent-foreground` | 악센트 버튼 텍스트 | 허용 |
| `--muted` | 비활성 영역, 칩 배경 | 허용 |
| `--muted-foreground` | 타임스탬프, 보조 캡션 | 허용 |
| `--destructive` (Danger) | 위험, 취약 암호, 삭제 액션 | **Red 계열 고정** |
| `--destructive-foreground` | 경고 버튼 텍스트 | 허용 |
| `--border` | 카드와 입력창 테두리 | 허용(시스템 구분선 사용 가능) |
| `--input` | 입력 필드 외곽선 | 허용 |
| `--ring` | 포커스 링 | 허용(시스템 포커스 표시 사용 가능) |
| `--overlay` | 모달 뒤 화면을 가리는 반투명 막 | 허용(시스템 시트의 기본 막 사용 가능) |

어느 플랫폼이든 텍스트와 배경의 대비는 WCAG AA(본문 4.5:1, 큰 글자 3:1) 이상이어야 한다.

---

## 3. 간격·모서리 토큰

간격과 모서리도 이름은 공통으로 쓴다. 값은 4pt 그리드를 기준으로 하며 [`tokens.json`](../Tools/design-tokens/tokens.json)에 적는다. 모서리는 플랫폼이 시스템 모양(예: Apple의 연속 곡률, Material 3의 shape scale)을 쓸 때 조정할 수 있다.

| 토큰 | 용도 |
| :--- | :--- |
| `space-1` | 아이콘과 레이블 사이 |
| `space-2` | 같은 그룹 안 요소 사이 |
| `space-3` | 입력 필드 안쪽 여백, 카드 `sm` 패딩 |
| `space-4` | 카드 `md` 패딩, 섹션 안 요소 사이 |
| `space-5` | 카드 `lg` 패딩 |
| `space-6` | 섹션 사이 |
| `radius-sm` | 칩, 배지 |
| `radius-md` | 버튼, 입력 필드, 알림 |
| `radius-lg` | 카드, 다이얼로그 |

---

## 4. 타이포그래피

| 용도 | 규칙 |
| :--- | :--- |
| UI 레이블·헤더 | 각 플랫폼의 시스템 산세리프를 쓴다(5장). |
| 비밀번호 텍스트 | **모노스페이스(고정폭) 글꼴을 반드시 쓴다.** 대문자 `I`, 소문자 `l`, 숫자 `1`, 특수문자 `\|`처럼 헷갈리는 글자를 구분하기 위해서다. 이 규칙은 모든 플랫폼에 공통이다. |
| 글자 크기 | 시스템의 동적 글자 크기(Dynamic Type, 브라우저 확대, Android 글꼴 배율)를 따른다. 고정 픽셀 크기로 막지 않는다. |

플랫폼별 글꼴 이름은 각 플랫폼의 `docs/platform-bindings.md`에 적는다.

### 4.1. 글자 역할 토큰

글자 크기는 역할 이름과 굵기만 공유하고, 실제 크기는 웹은 rem, Apple은 Dynamic Type 스타일로 매핑한다([ADR-0027](adr/ADR-0027-typography-role-tokens.md)). 값은 [`tokens.json`](../Tools/design-tokens/tokens.json)의 `typography` 그룹에 있다.

| 역할 | 용도 | 웹 클래스 | Apple |
| :--- | :--- | :--- | :--- |
| `display` | 화면 대표 제목 | `text-display` | `DesignToken.Typography.display` |
| `title` | 화면·시트 제목 | `text-title` | `.title` |
| `heading` | 섹션 제목, 카드 제목 | `text-heading` | `.heading` |
| `body` | 본문, 주요 버튼 | `text-body` | `.body` |
| `label` | 기본 UI 글자, 입력·목록 | `text-label` | `.label` |
| `caption` | 보조 설명, 오류·안내 문구 | `text-caption` | `.caption` |

강조는 굵기만 바꾼다(웹 `font-semibold`, Apple `.weight(_:)`). 고정폭은 웹 `font-mono`, Apple `<역할>Mono`를 쓴다.

---

## 5. 플랫폼 표현 규칙

표현층과 네이티브 컨트롤층의 기본 방향이다. 세부 매핑은 각 플랫폼의 `docs/platform-bindings.md`에 적는다.

| 플랫폼 | 디자인 언어 | 재질·모양 | 네이티브 컨트롤 | 색상 |
| :--- | :--- | :--- | :--- | :--- |
| Apple (iOS·macOS) | Liquid Glass | 시스템 재질을 그대로 쓴다. 유리 효과를 직접 흉내 내지 않는다. | 시스템 `Button`·`TextField`·`Toggle`을 감싸서 쓰고 브랜드 악센트만 입힌다. | 시멘틱 토큰을 `Color` 에셋으로 연결한다. |
| Android (도입 시) | Material 3 | M3 shape scale과 elevation을 쓴다. | M3 컴포넌트를 감싸서 쓴다. | 시멘틱 토큰을 `ColorScheme`에 매핑한다. **다이내믹 컬러는 쓰지 않는다.** |
| Web | 브랜드 표현 | 3장 토큰을 그대로 쓴다. | 직접 소유한 컴포넌트를 쓴다. | 2장 기준값을 그대로 쓴다. |
| 데스크톱 (Tauri) | 웹 표현을 따름 | 웹과 같다. | 웹과 같다. 창 테두리, 메뉴, 시스템 글꼴만 OS를 따른다. | 2장 기준값을 그대로 쓴다. |

---

## 6. 컴포넌트 계약

각 컴포넌트의 이름, 역할, 속성, 상태, 접근성 규칙은 모든 플랫폼에 공통이다. 모양은 5장을 따른다. 괄호 안은 규격상 이름이고, 각 플랫폼의 실제 파일·타입 이름은 다를 수 있다.

### 6.1. 구현 현황

| 컴포넌트 | Web | Apple |
| :--- | :--- | :--- |
| `Button` (`KMButton`) | 구현 | 구현 |
| `Card` (`KMCard`) | 구현 | 구현 |
| `PasswordInputField` (`KMPasswordField`) | 구현 | 구현 |
| `EntropyGauge` (`KMGaugeBar`) | 구현 | 구현 |
| `BurnTimerBadge` (`KMBurnBadge`) | 구현 | 구현 |
| `Alert` (`KMAlert`) | 구현 | 미구현 |
| `ConfirmPrompt` (`KMConfirmPrompt`) | 구현 | 미구현(시스템 확인 대화상자 사용) |
| `ModalDialog` (`KMModal`) | 구현 | 미구현(시스템 시트 사용) |

### 6.2. `Button` (`KMButton`)

암호 생성, 볼트 저장, 클립보드 복사 같은 주요 액션을 실행한다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `variant` | `primary` \| `outline` \| `destructive` \| `ghost` | | `primary`: 주요 액션, `outline`: 보조 액션, `destructive`: 위험한 액션, `ghost`: 아이콘 전용 액션 |
| `size` | `sm` \| `md` \| `lg` | `md` | 최소 높이 `sm` 32pt, `md` 40pt, `lg` 48pt. 터치 플랫폼에서는 터치 영역을 44pt 이상으로 둔다. |
| `title` | String | | 버튼 텍스트 |
| `icon` | 아이콘 (선택) | | 왼쪽·오른쪽 아이콘 |
| `isLoading` | Boolean | false | 로딩 표시. 로딩 중에는 다시 누를 수 없다. |
| `isDisabled` | Boolean | false | 비활성 상태 |
| `action` | `() -> Void` | | 탭·클릭 콜백 |

- 접근성: `ghost`처럼 텍스트가 없는 버튼은 접근성 레이블을 반드시 단다.

### 6.3. `Card` (`KMCard`)

계정 자격증명 항목과 섹션을 담는다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `padding` | `sm` \| `md` \| `lg` | `md` | `space-3`, `space-4`, `space-5` |
| `isInteractive` | Boolean | false | 탭할 수 있는 목록 항목인지. 켜면 플랫폼의 hover·press 피드백을 쓴다. |
| `content` | 뷰 슬롯 | | 안에 넣을 뷰 |

### 6.4. `PasswordInputField` (`KMPasswordField`)

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
3. 값은 4장의 모노스페이스 규칙을 따른다.
4. 접근성: 보기·숨김 버튼과 복사 버튼에 각각 레이블을 단다.

### 6.5. `EntropyGauge` (`KMGaugeBar`)

비밀번호 강도(0~100점)를 실시간 진행 막대로 보여 준다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `score` | Int (0~100) | | 엔트로피 점수 |
| `showLabel` | Boolean | true | 상태 텍스트("취약함" / "보통" / "초강력") 표시 |
| `animated` | Boolean | true | 점수가 바뀔 때 게이지가 부드럽게 차오르는 애니메이션. 시스템의 동작 줄이기 설정을 따른다. |

| 점수 | 색상 토큰 | 문구 |
| :--- | :--- | :--- |
| 0~59점 | `--destructive` | 취약함 |
| 60~79점 | `--primary` | 보통 |
| 80~100점 | `--accent` | 초강력 |

- 접근성: 색만으로 강도를 전하지 않는다. `showLabel`이 꺼져 있어도 접근성 값으로 문구를 제공한다.

### 6.6. `BurnTimerBadge` (`KMBurnBadge`)

클립보드 복사 뒤 30초 카운트다운과 자동 비우기 상태를 보여 준다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `remainingSeconds` | Int (30 → 0) | | 남은 시간 |
| `totalSeconds` | Int | 30 | 전체 타이머 시간 |
| `onBurned` | `() -> Void` | | 0초가 되어 비우기가 끝났을 때 콜백 |

표시 형태: `"30s"` 원형 축소 배지 또는 화면 위쪽 1px 진행 막대

### 6.7. `Alert` (`KMAlert`)

오류나 안내를 화면 안에 표시한다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `variant` | `error` \| `info` | `error` | `error`: `--destructive` 계열, `info`: `--muted` 계열 |
| `title` | String (선택) | | 제목 |
| `content` | 뷰 슬롯 | | 본문 |
| `actions` | 뷰 슬롯 (선택) | | 재시도 같은 후속 액션 |
| `live` | Boolean | true | 보조 기술에 바로 알릴지 |

- 접근성: `live`가 켜져 있으면 `error`는 즉시 알림(웹 `role="alert"`), `info`는 상태 알림(웹 `role="status"`)으로 전한다.

### 6.8. `ConfirmPrompt` (`KMConfirmPrompt`)

되돌리기 어려운 액션 전에 확인을 받는다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `title` | String | | 제목 |
| `body` | String | | 무엇이 일어나는지 설명 |
| `confirmLabel` | String | | 확인 버튼 문구. 일어날 일을 동사로 쓴다(예: "삭제"). |
| `cancelLabel` | String | "취소" | 취소 버튼 문구 |
| `tone` | `destructive` \| `notice` | | 확인 버튼이 `destructive` 또는 `primary` 변형을 쓴다. |
| `pending` | Boolean | false | 처리 중. 확인 버튼에 로딩을 표시하고 취소를 막는다. |
| `onConfirm` / `onCancel` | `() -> Void` | | 콜백 |

- 처음 초점은 **취소** 버튼에 둔다. 실수로 확인하는 일을 막기 위해서다.
- 접근성: 웹은 `role="alertdialog"`, Apple은 시스템 확인 대화상자(`confirmationDialog`·`alert`)를 쓴다.

### 6.9. `ModalDialog` (`KMModal`)

화면 위에 별도 작업 영역을 띄운다.

| 속성 | 타입 | 기본값 | 뜻 |
| :--- | :--- | :--- | :--- |
| `title` | 제목 참조 | | 보조 기술이 읽을 제목 |
| `description` | 본문 참조 | | 보조 기술이 읽을 설명 |
| `content` | 뷰 슬롯 | | 안에 넣을 뷰 |
| `onDismiss` | `() -> Void` | | 닫기 콜백(Esc 키, 바깥 탭, 닫기 버튼) |

- 열려 있는 동안 초점은 대화상자 안에 머문다.
- Apple은 시스템 시트(`sheet`)를, Android는 M3 다이얼로그나 바텀 시트를 쓴다.

---

## 7. 아이콘

모든 플랫폼은 아래 용도마다 같은 의미의 아이콘을 쓴다. 모양은 각 플랫폼의 시스템 아이콘 세트(SF Symbols, Material Symbols, 웹 아이콘 세트)를 따른다. 플랫폼별 아이콘 이름은 각 플랫폼의 `docs/platform-bindings.md`에 적는다.

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
