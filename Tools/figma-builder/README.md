# KeyManage DS Builder (Figma 개발 플러그인)

Figma 파일에 `KeyManage`의 **shadcn/ui 기반 시멘틱 디자인 시스템, 토큰, 5대 핵심 컴포넌트, 플랫폼별 화면 흐름(목업 24개와 이동 화살표)**를 원클릭으로 자동 생성하는 플러그인입니다. 

외부 네트워크나 MCP 커넥터 호출 한도 없이, Figma 데스크톱 앱 안에서 100% 로컬로 초고속 실행됩니다.

---

## 1. 생성되는 내용

토큰 값은 [`tokens.json`](../design-tokens/tokens.json)에서 온다. `node Tools/design-tokens/generate.mjs`가 `code.js`의 `@generated:tokens` 블록을 다시 쓰므로, 토큰을 바꾸면 생성기를 돌린 뒤 플러그인을 다시 실행하면 된다(ADR-0026). 블록은 직접 고치지 않는다.

1. **로컬 변수 (Variables)**: `KeyManage` 컬렉션 하나에 **Light / Dark 모드**로 등록한다. 이름은 코드와 같다.
   * `color/<이름>` 16개 (웹 `bg-<이름>`, Apple `DesignToken.View.<이름>`)
   * `space/1`~`space/6`, `radius/sm·md·lg` (숫자 변수)
   * 무료(Starter) 플랜처럼 모드를 하나만 쓸 수 있으면 Light 값만 넣고 경고를 띄운다.
2. **텍스트 스타일**: `typography/<역할>`과 `typography/<역할>-mono` (역할 6개, ADR-0027). 설명란에 웹 클래스와 Apple 이름을 적는다. 글꼴은 Figma에서 Inter·Roboto Mono로 그린다.
3. **`🎨 Tokens` 페이지**: 색 견본(light / dark 값)과 글자 역할 견본.
4. **`🧩 Components` 페이지**:
   * 5대 핵심 컴포넌트 베리언트:
     1. `KMButton` (Primary / Outline / Destructive / Accent)
     2. `KMPasswordField` (마스킹 토글, 모노스페이스)
     3. `KMGaugeBar` (3단계 엔트로피 강도 게이지)
     4. `KMBurnBadge` (30초 자동 소각 타이머 배지)
     5. `KMCard` (볼트 계정 카드)
5. **`📱 Screens` 페이지 (화면 흐름: 플랫폼별 목업 + 이동 화살표)**: 화면 흐름 8단계(로그인 → 계정 만들기 → 잠금 해제 → 볼트 → 항목 상세 → 비밀번호 생성기 → 설정 → 계정 삭제 확인)를 **iPhone · Android · Web 세 줄**, 모두 24개 화면으로 그린다.
   * 색은 모두 `KeyManage` 변수에 바인딩된다. 크기·모서리·내비게이션(iPhone 탭 바, Android 내비게이션 바, Web 사이드바)은 각 플랫폼 관례를 따른다(`docs/design-system.md` 5.1절 🟥).
   * 화면은 기기 틀(iPhone·Android)과 브라우저 창(Web) 안에 놓인다. 기기·창 색은 하드웨어 색이라 고정값이다.
   * 한글은 `Noto Sans KR`로 그린다. 없으면 Inter로 내려가며 경고를 띄운다.
   * **라이트 구역과 다크 구역**을 위아래로 그린다(각 24개, 모두 48개). 무료 플랜처럼 모드가 하나면 다크 구역은 `KeyManage Dark` 컬렉션 변수에, 모드를 쓸 수 있으면 같은 변수에 Dark 모드를 지정해 그린다.
   * 화면 위로 **이동 화살표**를 그린다(예: 로그인 → 볼트 "로그인 성공", 볼트 → 설정 "설정 탭"). 플랫폼 줄마다 같은 흐름이 반복된다.

> 이 플러그인은 `🎨 Tokens`, `🧩 Components`, `📱 Screens` 세 페이지를 비우고 다시 그린다. 예전 버전이 만든 `🔀 User Flow` 페이지가 있으면 지운다. 손으로 그린 화면은 다른 페이지에 두어야 한다.

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
