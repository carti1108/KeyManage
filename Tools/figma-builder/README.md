# KeyManage DS Builder (Figma 개발 플러그인)

Figma 파일에 `KeyManage`의 **shadcn/ui 기반 시멘틱 디자인 시스템, 토큰, 5대 핵심 컴포넌트, 그리고 실제 모바일 화면 2개**를 원클릭으로 자동 생성하는 플러그인입니다. 

외부 네트워크나 MCP 커넥터 호출 한도 없이, Figma 데스크톱 앱 안에서 100% 로컬로 초고속 실행됩니다.

---

## 1. 생성되는 내용

1. **로컬 변수 (Variables)**:
   * `primitive`: `slate/900`, `emerald/500`, `sky/400`, `red/500` 등 기본 팔레트 등록.
   * `semantic`: `--background`, `--card`, `--primary`, `--accent`, `--destructive` 등 14개 shadcn 시멘틱 변수 자동 등록 및 바인딩.
2. **텍스트 스타일 (Text Styles)**:
   * `heading/large`, `heading/title`, `body/medium`, `body/regular`, `password/mono`, `caption/small` 등록.
3. **`🎨 Tokens` 페이지**:
   * 시멘틱 색상 칩 견본 및 타이포그래피 스케일 시각화.
4. **`🧩 Components` 페이지**:
   * 5대 핵심 컴포넌트 베리언트:
     1. `KMButton` (Primary / Outline / Destructive / Accent)
     2. `KMPasswordField` (마스킹 토글, 모노스페이스)
     3. `KMGaugeBar` (3단계 엔트로피 강도 게이지)
     4. `KMBurnBadge` (30초 자동 소각 타이머 배지)
     5. `KMCard` (볼트 계정 카드)
5. **`📱 Screens` 페이지**:
   * iPhone 16 Pro 규격 (393 x 852) 오토레이아웃 실화면 2종:
     * **볼트 대시보드 (`VaultDashboard`)**: 전체 보안 점수(96점) 요약 및 계정 리스트.
     * **스마트 비밀번호 생성기 (`PasswordGenerator`)**: 생성된 모노스페이스 암호, 복사 버튼, 옵션 박스.

---

## 2. Figma에서 실행하는 방법

1. **Figma 데스크톱 앱**을 열고, 빈 디자인 파일을 하나 생성합니다.
2. 상단 메뉴 또는 단축키 `Ctrl + /` (Mac: `Cmd + /`)를 누릅니다.
3. 검색창에 **`Import plugin from manifest...`** (또는 `"매니페스트에서 플러그인 가져오기"`)를 입력하고 실행합니다.
4. 파일 선택창에서 이 폴더의 **`Tools/figma-builder/manifest.json`** 파일을 선택합니다.
5. 이제 언제든지 `Ctrl + /` → **`KeyManage DS Builder`** 입력 후 `Enter`를 치면 3초 만에 전체 디자인 시스템과 화면이 자동으로 빌드됩니다!

---

## 3. 수정 및 커스터마이징

* `Tools/figma-builder/code.js` 파일을 수정한 뒤, Figma에서 플러그인을 다시 실행하면 변경된 코드가 즉시 반영되어 페이지를 다시 그립니다.
