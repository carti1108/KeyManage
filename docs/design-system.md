# [Design] shadcn/ui 기반 크로스 플랫폼 디자인 시스템 (Design System)

이 문서는 `KeyManage`의 모든 클라이언트(Web·데스크톱 Vite + React, iOS SwiftUI/UIKit, Android Compose)가 **shadcn/ui**의 디자인 철학(소유, 시멘틱 토큰, 미니멀리즘)과 접근성을 동일하게 공유하기 위해 정의된 통합 디자인 시스템 명세입니다.

---

## 1. 디자인 철학 및 기조 (Swiss Style & Minimalist)

* **Copy, Own & Customize (shadcn/ui 철학)**: 외부 무거운 UI 라이브러리에 종속되지 않고, 컴포넌트의 소유권을 프로젝트가 직접 가집니다.
* **보안 신뢰 중심 (Trust & Vault)**: 깊이감 있는 Slate Navy 배경에 보안 상태를 직관적으로 전달하는 Emerald Green(안전)과 Red(위험) 악센트.
* **플랫폼 중립적 토큰 (Neutral Semantic Tokens)**: 웹의 CSS 변수, iOS의 `UIColor`/`Color`, 안드로이드의 `ColorScheme`이 1:1로 정확히 동기화됩니다.

---

## 2. shadcn/ui 표준 시멘틱 토큰 (Color Tokens)

모든 플랫폼은 아래의 shadcn/ui 표준 명칭과 Hex/HSL 값을 공통으로 사용합니다.

| 시멘틱 토큰 명칭 | 라이트 모드 (Light) | 다크 모드 (Dark - 기본 권장) | 용도 |
| :--- | :--- | :--- | :--- |
| `--background` | `#F8FAFC` (Slate 50) | `#0F172A` (Slate 900) | 전체 뷰 기본 배경 |
| `--foreground` | `#0F172A` (Slate 900) | `#F8FAFC` (Slate 50) | 기본 본문 텍스트 |
| `--card` | `#FFFFFF` (White) | `#1E293B` (Slate 800) | 볼트 카드, 다이얼로그 표면 |
| `--card-foreground` | `#0F172A` (Slate 900) | `#F8FAFC` (Slate 50) | 카드 내부 텍스트 |
| `--primary` | `#1E3A5F` (Navy 800) | `#38BDF8` (Sky 400) | 주요 브랜드 액션, 활성 탭 |
| `--primary-foreground` | `#FFFFFF` | `#0F172A` | 주요 버튼 텍스트 |
| `--accent` (Secure) | `#059669` (Emerald 600) | `#10B981` (Emerald 500) | 안전한 비밀번호, 생성/저장 강조 |
| `--accent-foreground` | `#FFFFFF` | `#0F172A` | 액센트 버튼 텍스트 |
| `--muted` | `#F1F5F9` (Slate 100) | `#1E293B` (Slate 800) | 비활성 영역, 칩 배경 |
| `--muted-foreground` | `#64748B` (Slate 500) | `#94A3B8` (Slate 400) | 타임스탬프, 보조 캡션 |
| `--destructive` (Danger) | `#DC2626` (Red 600) | `#EF4444` (Red 500) | 취약 암호 경고, 삭제 액션 |
| `--destructive-foreground` | `#FFFFFF` | `#FFFFFF` | 경고 버튼 텍스트 |
| `--border` | `#E2E8F0` (Slate 200) | `rgba(255, 255, 255, 0.08)` | 카드 및 입력창 테두리 |
| `--input` | `#E2E8F0` (Slate 200) | `#334155` (Slate 700) | 인풋 필드 아웃라인 |
| `--ring` | `#38BDF8` (Sky 400) | `#38BDF8` (Sky 400) | 포커스 포인터 링 |

---

## 3. 플랫폼별 토큰 바인딩 규격

### 3.1. Web·데스크톱 (Vite + React + Tailwind CSS, ADR-0012)
* 토큰은 `Apps/Web/src/index.css`에 shadcn/ui와 같은 이름의 CSS 변수로 둔다. `:root`가 라이트, `.dark`가 다크이며 기본은 다크(`index.html`의 `<html class="dark">`)다.
* Tailwind v4는 설정 파일 없이 CSS의 `@theme inline`으로 변수를 유틸리티에 연결한다(`bg-card`, `text-primary` 등).
* 컴포넌트는 `Apps/Web/src/ui/components/`에 둔다. 클래스 병합은 `clsx`와 `tailwind-merge`, 아이콘은 `lucide-react`를 쓴다. Radix는 다이얼로그처럼 필요한 컴포넌트가 생길 때 추가한다.
* 웹 글꼴(Poppins·Inter)은 데스크톱 CSP와 외부 요청 문제로 아직 넣지 않고 시스템 글꼴을 쓴다. 비밀번호는 `font-mono`로 표시한다.

### 3.2. iOS (SwiftUI & UIKit 공용)
* iOS 구현이 **SwiftUI**가 되든 **UIKit**이 되든 상관없이 모두 지원할 수 있도록 `DesignToken` 정적 열거형 구조를 유지합니다:
```swift
public enum DesignToken {
    // UIColor (UIKit 지원)
    public enum UI {
        public static let background = UIColor(named: "ColorBackground") ?? UIColor(hex: "#0F172A")
        public static let card = UIColor(named: "ColorCard") ?? UIColor(hex: "#1E293B")
        public static let primary = UIColor(named: "ColorPrimary") ?? UIColor(hex: "#38BDF8")
        public static let accent = UIColor(named: "ColorAccent") ?? UIColor(hex: "#10B981")
        public static let destructive = UIColor(named: "ColorDestructive") ?? UIColor(hex: "#EF4444")
    }
    
    // SwiftUI Color (SwiftUI 지원)
    public enum View {
        public static let background = Color(uiColor: UI.background)
        public static let card = Color(uiColor: UI.card)
        public static let primary = Color(uiColor: UI.primary)
        public static let accent = Color(uiColor: UI.accent)
        public static let destructive = Color(uiColor: UI.destructive)
    }
}
```

### 3.3. Android (Jetpack Compose)
* Material3 `darkColorScheme()` 및 `lightColorScheme()`에 위 Hex 값을 1:1 주입하여 Compose `MaterialTheme.colorScheme.*` 형태로 소비.

---

## 4. 타이포그래피 (Typography)

* **UI 레이블/헤더**: 기하학적 산세리프 계열
  * Web: `Poppins` (헤더) / `Inter` (본문)
  * iOS: `SF Pro` (시스템)
  * Android: `Roboto` (시스템)
* **비밀번호 텍스트 (필수 규격)**:
  * 오독 방지(대문자 `I`, 소문자 `l`, 숫자 `1`, 특수문자 `|` 등)를 위해 **모노스페이스 고정폭 폰트**를 강제 적용합니다.
  * Web: `font-mono` (JetBrains Mono / SF Mono)
  * iOS (SwiftUI): `Font.system(.body, design: .monospaced)`
  * iOS (UIKit): `UIFont.monospacedSystemFont(ofSize: 16, weight: .medium)`
  * Android: `FontFamily.Monospace`

---

## 5. 핵심 UI 컴포넌트 카탈로그 (5대 핵심 컴포넌트)

외부 라이브러리 설치 없이 각 플랫폼 프로젝트 내에 직접 복사하여 소유하는 컴포넌트 규격입니다.

### 5.1. `Button` (`KMButton`)
* **역할**: 암호 생성, 볼트 저장, 클립보드 복사 등 주요 액션 트리거.
* **파라미터 / Props**:
  * `variant`: `primary` (솔리드 스카이), `outline` (테두리 투명 배경), `destructive` (레드), `ghost` (아이콘 전용 투명 버튼)
  * `size`: `sm` (높이 32pt), `md` (높이 40pt, 기본값), `lg` (높이 48pt)
  * `title`: 버튼 텍스트 (String)
  * `icon`: 좌/우측 아이콘 (Optional)
  * `isLoading`: 로딩 스피너 표시 여부 (Boolean, 기본 false)
  * `isDisabled`: 비활성화 상태 (Boolean, 기본 false)
  * `action`: 탭/클릭 콜백 이벤트 (`() -> Void`)

### 5.2. `Card` (`KMCard`)
* **역할**: 계정 자격증명 항목 및 섹션 컨테이너.
* **파라미터 / Props**:
  * `padding`: `sm` (12pt), `md` (16pt, 기본값), `lg` (20pt)
  * `isInteractive`: 탭 가능한 리스트 아이템인지 여부 (Boolean, 기본 false — hover/press 애니메이션 활성화)
  * `content`: 내부 뷰 슬롯

### 5.3. `PasswordInputField` (`KMPasswordField`)
* **역할**: 비밀번호 입력 및 마스킹/평문 토글, 원클릭 복사 기능이 일체화된 필드.
* **파라미터 / Props**:
  * `text`: 입력값 바인딩 (String)
  * `placeholder`: 플레이스홀더 텍스트 (기본: "비밀번호 입력")
  * `isSecured`: 마스킹 여부 (Boolean, 기본 true — `••••••••` 표시)
  * `showCopyAction`: 우측 복사 버튼 노출 여부 (Boolean, 기본 true)
  * `onCopy`: 복사 버튼을 눌렀을 때 부르는 콜백 (`() -> Void`). 실제 클립보드 쓰기와 30초 소각(COM-001)은 컴포넌트가 아니라 이를 받은 쪽이 맡는다
  * `onTextChange`: 텍스트 변경 이벤트 (`(String) -> Void`)
* **내부 동작**: 평문 토글 후 10초간 조작이 없으면 보안을 위해 자동으로 `isSecured = true`로 복귀.

### 5.4. `EntropyGauge` (`KMGaugeBar`)
* **역할**: 비밀번호 보안 강도(0~100점)를 시각적으로 보여주는 실시간 프로그레스 바.
* **파라미터 / Props**:
  * `score`: 엔트로피 점수 (Int, 0~100)
  * `showLabel`: 상태 텍스트("취약함" / "보통" / "초강력") 표시 여부 (Boolean, 기본 true)
  * `animated`: 점수 변경 시 부드러운 게이지 차오름 애니메이션 적용 여부 (Boolean, 기본 true)
* **색상 규칙**: 0~59점 `--destructive`(Red), 60~79점 `--primary`(Sky), 80~100점 `--accent`(Emerald).

### 5.5. `BurnTimerBadge` (`KMBurnBadge`)
* **역할**: 클립보드 복사 후 30초 카운트다운 및 자동 소각 상태 시각화.
* **파라미터 / Props**:
  * `remainingSeconds`: 남은 시간 (Int, 30 -> 0초)
  * `totalSeconds`: 전체 타이머 시간 (Int, 기본 30)
  * `onBurned`: 0초 도달 시 소각 완료 콜백 이벤트 (`() -> Void`)
* **디스플레이**: `"30s"` 원형 축소 배지 또는 상단 1px 슬라이딩 프로그레스 바.

---

## 6. 플랫폼별 아이콘 매핑표 (1:1 표준)

| 용도 | Web (Lucide React) | iOS (SF Symbols: UIKit/SwiftUI 공용) | Android (Material Icons) |
| :--- | :--- | :--- | :--- |
| **비밀번호 보기** | `<Eye />` | `eye` | `Icons.Default.Visibility` |
| **비밀번호 숨김** | `<EyeOff />` | `eye.slash` | `Icons.Default.VisibilityOff` |
| **클립보드 복사** | `<Copy />` | `doc.on.doc` | `Icons.Default.ContentCopy` |
| **복사 완료** | `<CheckCircle />` | `checkmark.circle.fill` | `Icons.Default.CheckCircle` |
| **암호 재생성** | `<RefreshCw />` | `arrow.clockwise` | `Icons.Default.Refresh` |
| **볼트 잠금** | `<Lock />` | `lock.fill` | `Icons.Default.Lock` |
| **경고/취약** | `<AlertTriangle />` | `exclamationmark.triangle.fill` | `Icons.Default.Warning` |
| **오프라인 배너** | `<WifiOff />` | `wifi.slash` | `Icons.Default.WifiOff` |
