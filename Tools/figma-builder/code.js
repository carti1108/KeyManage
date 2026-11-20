// KeyManage DS Builder — Figma Development Plugin
// Builds the KeyManage design system, tokens, components, and screens in Figma.
// Compatible with fresh empty Figma files (auto-creates variable collections).

// 어떤 code.js가 실행됐는지 알림창에서 바로 구분하기 위한 표시
const BUILD = 'tokens.json 연결판 (ADR-0026)';
const LOG = [];
const step = async (name, fn) => {
  try {
    return await fn();
  } catch (error) {
    const msg = `${name}: ${error && error.message ? error.message : error}`;
    LOG.push(msg);
    console.error(msg);
    return null;
  }
};

// ==========================================
// 1. Tokens (Tools/design-tokens/tokens.json에서 주입, ADR-0026)
// ==========================================
// 아래 블록은 node Tools/design-tokens/generate.mjs가 다시 쓴다. 직접 고치지 않는다.
// @generated:tokens:start
const TOKENS = {
  "colors": [
    {
      "name": "background",
      "light": "#FFFFFF",
      "dark": "#000000"
    },
    {
      "name": "foreground",
      "light": "#0A0A0A",
      "dark": "#FAFAFA"
    },
    {
      "name": "card",
      "light": "#FFFFFF",
      "dark": "#141414"
    },
    {
      "name": "card-foreground",
      "light": "#0A0A0A",
      "dark": "#FAFAFA"
    },
    {
      "name": "primary",
      "light": "#0A0A0A",
      "dark": "#FAFAFA"
    },
    {
      "name": "primary-foreground",
      "light": "#FFFFFF",
      "dark": "#0A0A0A"
    },
    {
      "name": "accent",
      "light": "#059669",
      "dark": "#10B981"
    },
    {
      "name": "accent-foreground",
      "light": "#FFFFFF",
      "dark": "#0A0A0A"
    },
    {
      "name": "muted",
      "light": "#F5F5F5",
      "dark": "#262626"
    },
    {
      "name": "muted-foreground",
      "light": "#737373",
      "dark": "#A3A3A3"
    },
    {
      "name": "destructive",
      "light": "#DC2626",
      "dark": "#EF4444"
    },
    {
      "name": "destructive-foreground",
      "light": "#FFFFFF",
      "dark": "#FFFFFF"
    },
    {
      "name": "border",
      "light": "#E5E5E5",
      "dark": "rgba(255, 255, 255, 0.12)"
    },
    {
      "name": "input",
      "light": "#E5E5E5",
      "dark": "#262626"
    },
    {
      "name": "ring",
      "light": "#0A0A0A",
      "dark": "#FAFAFA"
    },
    {
      "name": "overlay",
      "light": "rgba(0, 0, 0, 0.4)",
      "dark": "rgba(0, 0, 0, 0.4)"
    }
  ],
  "space": [
    {
      "key": "1",
      "value": 4
    },
    {
      "key": "2",
      "value": 8
    },
    {
      "key": "3",
      "value": 12
    },
    {
      "key": "4",
      "value": 16
    },
    {
      "key": "5",
      "value": 20
    },
    {
      "key": "6",
      "value": 24
    }
  ],
  "radius": [
    {
      "key": "sm",
      "value": 6
    },
    {
      "key": "md",
      "value": 8
    },
    {
      "key": "lg",
      "value": 12
    }
  ],
  "typography": [
    {
      "name": "display",
      "fontSize": 1.5,
      "lineHeight": 2,
      "fontWeight": 700,
      "textStyle": "largeTitle"
    },
    {
      "name": "title",
      "fontSize": 1.25,
      "lineHeight": 1.75,
      "fontWeight": 600,
      "textStyle": "title2"
    },
    {
      "name": "heading",
      "fontSize": 1.125,
      "lineHeight": 1.75,
      "fontWeight": 600,
      "textStyle": "headline"
    },
    {
      "name": "body",
      "fontSize": 1,
      "lineHeight": 1.5,
      "fontWeight": 400,
      "textStyle": "body"
    },
    {
      "name": "label",
      "fontSize": 0.875,
      "lineHeight": 1.25,
      "fontWeight": 400,
      "textStyle": "subheadline"
    },
    {
      "name": "caption",
      "fontSize": 0.75,
      "lineHeight": 1,
      "fontWeight": 400,
      "textStyle": "footnote"
    }
  ]
};
// @generated:tokens:end

// Figma 텍스트 스타일 글꼴: 웹은 시스템 산세리프, Apple은 SF Pro라 Figma에서는 Inter로 그린다(표현층, ADR-0025)
const SANS = 'Inter';
const MONO = 'Roboto Mono';
const WEIGHT_STYLE = { 400: 'Regular', 500: 'Medium', 600: 'Semi Bold', 700: 'Bold' };

let COL = {};
let ALL_VARS = [];
let TS = {};

// Helper: #RRGGBB, #RRGGBBAA, rgba(r, g, b, a) -> Figma RGBA
function hexToRgba(value) {
  const rgba = value.match(/^rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)$/);
  if (rgba) return { r: rgba[1] / 255, g: rgba[2] / 255, b: rgba[3] / 255, a: Number(rgba[4]) };
  const v = value.slice(1);
  const n = (i) => parseInt(v.slice(i, i + 2), 16) / 255;
  return { r: n(0), g: n(2), b: n(4), a: v.length === 8 ? parseInt(v.slice(6, 8), 16) / 255 : 1 };
}

function findVar(name) {
  return ALL_VARS.find((v) => v.variableCollectionId === COL.id && v.name === name);
}

// 모드별 값을 매번 다시 넣어 tokens.json 변경이 기존 파일에도 반영되게 한다
function ensureVariable(name, type, values, scopes) {
  let v = findVar(name);
  if (!v) {
    v = figma.variables.createVariable(name, COL, type);
    ALL_VARS.push(v);
  }
  v.setValueForMode(MODES.light, values.light);
  if (MODES.dark) v.setValueForMode(MODES.dark, values.dark);
  v.scopes = scopes;
  return v;
}

const V = (name) => {
  const v = findVar(name);
  if (!v) throw new Error(`KeyManage 변수 없음: ${name}`);
  return v;
};

// ==========================================
// 2. Setup Variables and Text Styles
// ==========================================

let MODES = {};
let DARK_COL = null;

function ensureDarkVariable(name, value) {
  let v = ALL_VARS.find((x) => x.variableCollectionId === DARK_COL.id && x.name === name);
  if (!v) {
    v = figma.variables.createVariable(name, DARK_COL, 'COLOR');
    ALL_VARS.push(v);
  }
  v.setValueForMode(DARK_COL.modes[0].modeId, value);
  v.scopes = ['ALL_FILLS', 'STROKE_COLOR'];
}

async function setupVariables() {
  const collections = await figma.variables.getLocalVariableCollectionsAsync();
  COL = collections.find((c) => c.name === 'KeyManage') || figma.variables.createVariableCollection('KeyManage');
  COL.renameMode(COL.modes[0].modeId, 'Light');
  MODES = { light: COL.modes[0].modeId, dark: COL.modes[1] ? COL.modes[1].modeId : null };
  if (!MODES.dark) {
    try {
      MODES.dark = COL.addMode('Dark');
    } catch (error) {
      // 무료(Starter) 플랜은 컬렉션당 모드가 하나뿐이다. Dark 값은 같은 이름으로 'KeyManage Dark' 컬렉션에 따로 둔다
      DARK_COL = collections.find((c) => c.name === 'KeyManage Dark') || figma.variables.createVariableCollection('KeyManage Dark');
    }
  }
  ALL_VARS = await figma.variables.getLocalVariablesAsync();

  for (const c of TOKENS.colors) {
    const name = `color/${c.name}`;
    ensureVariable(name, 'COLOR', { light: hexToRgba(c.light), dark: hexToRgba(c.dark) }, ['ALL_FILLS', 'STROKE_COLOR']);
    if (DARK_COL) ensureDarkVariable(name, hexToRgba(c.dark));
  }
  for (const s of TOKENS.space) ensureVariable(`space/${s.key}`, 'FLOAT', { light: s.value, dark: s.value }, ['GAP', 'WIDTH_HEIGHT']);
  for (const r of TOKENS.radius) ensureVariable(`radius/${r.key}`, 'FLOAT', { light: r.value, dark: r.value }, ['CORNER_RADIUS']);
}

async function setupTextStyles() {
  // 글꼴마다 굵기 이름 표기가 달라(Inter 'Semi Bold', Roboto Mono 'SemiBold') 후보를 차례로 시도하고,
  // 실제로 불러온 이름을 쓴다. 해당 굵기가 없으면 더 가는 굵기로 내려간다
  const loaded = {};
  async function loadFont(family, weight) {
    const key = `${family}:${weight}`;
    if (key in loaded) return loaded[key];
    const order = Object.keys(WEIGHT_STYLE).map(Number).filter((w) => w <= weight).reverse();
    for (const w of order) {
      for (const style of [WEIGHT_STYLE[w], WEIGHT_STYLE[w].replace(' ', '')]) {
        try {
          await figma.loadFontAsync({ family, style });
          if (w !== weight) LOG.push(`${family} ${WEIGHT_STYLE[weight]} 대신 ${style} 사용`);
          return (loaded[key] = style);
        } catch (error) {
          // 다음 후보를 시도한다
        }
      }
    }
    throw new Error(`${family} 글꼴을 불러올 수 없습니다`);
  }

  const localStyles = await figma.getLocalTextStylesAsync();
  for (const t of TOKENS.typography) {
    for (const [suffix, family] of [['', SANS], ['-mono', MONO]]) {
      const name = `typography/${t.name}${suffix}`;
      let ts = localStyles.find((s) => s.name === name);
      if (!ts) ts = figma.createTextStyle();
      ts.name = name;
      ts.fontName = { family, style: await loadFont(family, t.fontWeight) };
      ts.fontSize = t.fontSize * 16;
      ts.lineHeight = { unit: 'PIXELS', value: t.lineHeight * 16 };
      ts.description = `웹 text-${t.name} · Apple DesignToken.Typography.${t.name}${suffix ? 'Mono' : ''} (${t.textStyle})`;
      TS[name] = ts.id;
    }
  }
}

// ==========================================
// 3. Drawing Helpers
// ==========================================

function paint(varName) {
  return figma.variables.setBoundVariableForPaint(
    { type: 'SOLID', color: { r: 0, g: 0, b: 0 } },
    'color',
    V(varName)
  );
}

function solidPaint(hex) {
  const rgba = hexToRgba(hex);
  return { type: 'SOLID', color: { r: rgba.r, g: rgba.g, b: rgba.b }, opacity: rgba.a };
}

async function createText(characters, styleName, colorVar = 'color/foreground') {
  const t = figma.createText();
  t.characters = characters;
  if (TS[styleName]) await t.setTextStyleIdAsync(TS[styleName]);
  t.fills = [paint(colorVar)];
  return t;
}

function createFrame(name, layoutMode = 'VERTICAL', padding = 16, gap = 12, radius = 12, fillVar = 'color/card') {
  const f = figma.createFrame();
  f.name = name;
  f.layoutMode = layoutMode;
  f.paddingLeft = padding;
  f.paddingRight = padding;
  f.paddingTop = padding;
  f.paddingBottom = padding;
  f.itemSpacing = gap;
  f.cornerRadius = radius;
  f.primaryAxisSizingMode = 'AUTO';
  f.counterAxisSizingMode = 'AUTO';
  if (fillVar) f.fills = [paint(fillVar)];
  else f.fills = [];
  return f;
}

// manifest의 documentAccess가 dynamic-page라 페이지 내용을 읽기 전에 loadAsync가 필요하다.
// 새로 만드는 노드가 이 페이지에 들어가도록 현재 페이지로 바꾼다
async function getOrCreatePage(name) {
  let page = figma.root.children.find((p) => p.name === name);
  if (!page) {
    page = figma.createPage();
    page.name = name;
  }
  await page.loadAsync();
  for (const child of [...page.children]) child.remove();
  await figma.setCurrentPageAsync(page);
  return page;
}

// ==========================================
// 4. Page 1: Tokens Specimen
// ==========================================

async function buildTokensPage() {
  const page = await getOrCreatePage('🎨 Tokens');

  const container = createFrame('Tokens Specimen Container', 'VERTICAL', 40, 40, 0, 'color/background');
  container.x = 0;
  container.y = 0;

  const header = await createText('KeyManage Design Tokens (tokens.json)', 'typography/display', 'color/foreground');
  container.appendChild(header);

  // 1. Colors Section
  const colorSection = createFrame('Color Tokens Section', 'VERTICAL', 24, 16, 16, 'color/card');
  const colorTitle = await createText('Semantic Color Tokens', 'typography/title', 'color/primary');
  colorSection.appendChild(colorTitle);

  const grid = figma.createFrame();
  grid.name = 'Colors Grid';
  grid.layoutMode = 'HORIZONTAL';
  grid.layoutWrap = 'WRAP';
  grid.itemSpacing = 16;
  grid.counterAxisSpacing = 16;
  grid.fills = [];
  grid.resize(800, 300);

  for (const token of TOKENS.colors) {
    const name = `color/${token.name}`;
    const chip = figma.createFrame();
    chip.name = name;
    chip.layoutMode = 'VERTICAL';
    chip.paddingLeft = 12;
    chip.paddingRight = 12;
    chip.paddingTop = 12;
    chip.paddingBottom = 12;
    chip.itemSpacing = 8;
    chip.cornerRadius = 8;
    chip.fills = [paint('color/muted')];
    chip.strokes = [paint('color/border')];
    chip.strokeWeight = 1;
    chip.resize(180, 110);

    const swatch = figma.createFrame();
    swatch.name = 'Swatch';
    swatch.resize(156, 44);
    swatch.cornerRadius = 6;
    swatch.fills = [paint(name)];
    chip.appendChild(swatch);

    const label = await createText(name.replace('color/', ''), 'typography/body', 'color/foreground');
    const subLabel = await createText(`${token.light} / ${token.dark}`, 'typography/caption', 'color/muted-foreground');
    chip.appendChild(label);
    chip.appendChild(subLabel);
    grid.appendChild(chip);
  }

  colorSection.appendChild(grid);
  container.appendChild(colorSection);

  // 2. Typography Section
  const typeSection = createFrame('Typography Section', 'VERTICAL', 24, 20, 16, 'color/card');
  const typeTitle = await createText('Typography Scale', 'typography/title', 'color/accent');
  typeSection.appendChild(typeTitle);

  for (const spec of TOKENS.typography) {
    const name = `typography/${spec.name}`;
    const row = figma.createFrame();
    row.layoutMode = 'HORIZONTAL';
    row.itemSpacing = 24;
    row.fills = [];
    row.primaryAxisAlignItems = 'CENTER';

    const sample = await createText(`Aa · ${name}`, name, 'color/foreground');
    const info = await createText(`${spec.fontSize * 16}px / ${spec.lineHeight * 16}px · ${spec.fontWeight} · Apple ${spec.textStyle}`, 'typography/caption', 'color/muted-foreground');
    row.appendChild(sample);
    row.appendChild(info);
    typeSection.appendChild(row);
  }

  container.appendChild(typeSection);
}

// ==========================================
// 5. Page 2: Core Components
// ==========================================

async function buildComponentsPage() {
  const page = await getOrCreatePage('🧩 Components');

  const container = createFrame('Components Specimen Container', 'VERTICAL', 40, 40, 0, 'color/background');
  container.x = 0;
  container.y = 0;

  const header = await createText('KeyManage Core 5 UI Components', 'typography/display', 'color/foreground');
  container.appendChild(header);

  // 1. Buttons (KMButton)
  const btnSection = createFrame('1. KMButton Variants', 'VERTICAL', 24, 16, 16, 'color/card');
  btnSection.appendChild(await createText('Buttons (Primary / Outline / Destructive / Ghost)', 'typography/title', 'color/foreground'));

  const btnRow = figma.createFrame();
  btnRow.layoutMode = 'HORIZONTAL';
  btnRow.itemSpacing = 16;
  btnRow.fills = [];

  // Primary
  const b1 = createFrame('KMButton [Primary]', 'HORIZONTAL', 16, 8, 8, 'color/primary');
  b1.paddingTop = 10; b1.paddingBottom = 10;
  b1.appendChild(await createText('비밀번호 생성', 'typography/body', 'color/primary-foreground'));
  btnRow.appendChild(b1);

  // Outline
  const b2 = createFrame('KMButton [Outline]', 'HORIZONTAL', 16, 8, 8, null);
  b2.paddingTop = 10; b2.paddingBottom = 10;
  b2.strokes = [paint('color/border')];
  b2.strokeWeight = 1;
  b2.appendChild(await createText('클립보드 복사', 'typography/body', 'color/foreground'));
  btnRow.appendChild(b2);

  // Destructive
  const b3 = createFrame('KMButton [Destructive]', 'HORIZONTAL', 16, 8, 8, 'color/destructive');
  b3.paddingTop = 10; b3.paddingBottom = 10;
  b3.appendChild(await createText('볼트 삭제', 'typography/body', 'color/destructive-foreground'));
  btnRow.appendChild(b3);

  // Accent (Secure Action)
  const b4 = createFrame('KMButton [Accent Secure]', 'HORIZONTAL', 16, 8, 8, 'color/accent');
  b4.paddingTop = 10; b4.paddingBottom = 10;
  b4.appendChild(await createText('볼트 잠금 해제', 'typography/body', 'color/accent-foreground'));
  btnRow.appendChild(b4);

  btnSection.appendChild(btnRow);
  container.appendChild(btnSection);

  // 2. KMPasswordField
  const passSection = createFrame('2. KMPasswordField', 'VERTICAL', 24, 16, 16, 'color/card');
  passSection.appendChild(await createText('Password Input Field (Masked & Monospace)', 'typography/title', 'color/foreground'));

  const field = createFrame('KMPasswordField [Container]', 'HORIZONTAL', 16, 12, 10, 'color/input');
  field.strokes = [paint('color/border')];
  field.strokeWeight = 1;
  field.resize(400, 52);
  field.primaryAxisAlignItems = 'SPACE_BETWEEN';
  field.counterAxisAlignItems = 'CENTER';

  const maskedText = await createText('••••••••••••••••', 'typography/body-mono', 'color/foreground');
  const actionText = await createText('보기 / 복사', 'typography/caption', 'color/primary');
  field.appendChild(maskedText);
  field.appendChild(actionText);

  passSection.appendChild(field);
  container.appendChild(passSection);

  // 3. KMGaugeBar (Entropy Score)
  const gaugeSection = createFrame('3. KMGaugeBar', 'VERTICAL', 24, 16, 16, 'color/card');
  gaugeSection.appendChild(await createText('Entropy Strength Gauge Bar (3-Tier Status)', 'typography/title', 'color/foreground'));

  // Gauge 96 (Secure)
  const g1 = createFrame('Gauge 96 [Secure]', 'VERTICAL', 16, 8, 8, 'color/muted');
  g1.resize(400, 64);
  const g1Bar = figma.createFrame();
  g1Bar.resize(368, 8);
  g1Bar.cornerRadius = 4;
  g1Bar.fills = [paint('color/input')];
  const g1Fill = figma.createFrame();
  g1Fill.resize(350, 8);
  g1Fill.cornerRadius = 4;
  g1Fill.fills = [paint('color/accent')];
  g1Bar.appendChild(g1Fill);
  g1.appendChild(await createText('초강력 비밀번호 · 96점', 'typography/caption', 'color/accent'));
  g1.appendChild(g1Bar);
  gaugeSection.appendChild(g1);

  // Gauge 38 (Danger)
  const g2 = createFrame('Gauge 38 [Danger]', 'VERTICAL', 16, 8, 8, 'color/muted');
  g2.resize(400, 64);
  const g2Bar = figma.createFrame();
  g2Bar.resize(368, 8);
  g2Bar.cornerRadius = 4;
  g2Bar.fills = [paint('color/input')];
  const g2Fill = figma.createFrame();
  g2Fill.resize(140, 8);
  g2Fill.cornerRadius = 4;
  g2Fill.fills = [paint('color/destructive')];
  g2Bar.appendChild(g2Fill);
  g2.appendChild(await createText('취약한 비밀번호 · 38점', 'typography/caption', 'color/destructive'));
  g2.appendChild(g2Bar);
  gaugeSection.appendChild(g2);

  container.appendChild(gaugeSection);

  // 4. KMBurnBadge
  const badgeSection = createFrame('4. KMBurnBadge', 'VERTICAL', 24, 16, 16, 'color/card');
  badgeSection.appendChild(await createText('Auto-Burn Timer Badge (30 Seconds)', 'typography/title', 'color/foreground'));

  const badge = createFrame('KMBurnBadge [Badge]', 'HORIZONTAL', 12, 6, 14, 'color/destructive');
  badge.paddingTop = 6; badge.paddingBottom = 6;
  badge.appendChild(await createText('⏱️ 30초 후 클립보드 자동 소각', 'typography/caption', 'color/destructive-foreground'));
  badgeSection.appendChild(badge);

  container.appendChild(badgeSection);

  // 5. KMCard (Vault Account Item)
  const cardSection = createFrame('5. KMCard (Vault Item)', 'VERTICAL', 24, 16, 16, 'color/card');
  cardSection.appendChild(await createText('Vault Credential Card', 'typography/title', 'color/foreground'));

  const vCard = createFrame('KMCard [Item]', 'VERTICAL', 16, 10, 12, 'color/muted');
  vCard.resize(400, 90);
  vCard.strokes = [paint('color/border')];
  vCard.strokeWeight = 1;

  const topRow = figma.createFrame();
  topRow.layoutMode = 'HORIZONTAL';
  topRow.fills = [];
  topRow.primaryAxisAlignItems = 'SPACE_BETWEEN';
  topRow.resize(368, 24);
  topRow.appendChild(await createText('GitHub', 'typography/title', 'color/foreground'));
  topRow.appendChild(await createText('96점 (안전)', 'typography/caption', 'color/accent'));

  vCard.appendChild(topRow);
  vCard.appendChild(await createText('octocat@github.com', 'typography/body', 'color/muted-foreground'));

  cardSection.appendChild(vCard);
  container.appendChild(cardSection);
}

// ==========================================
// 6. Page 3: 화면 흐름 (플랫폼별 목업 8단계 + 이동 화살표)
// ==========================================
// 같은 화면을 세 플랫폼 줄로 그린다. 의미(색·역할)는 토큰 변수를 쓰고, 모양(크기·모서리·내비게이션)은
// 각 플랫폼 관례를 따른다(design-system.md 5.1절 🟥 항목). Flow Builder(flow/)의 그리기 방식을 옮겨 왔다.

const FLOW_STEPS = [
  { key: 'login', name: '로그인', path: '/login' },
  { key: 'signup', name: '계정 만들기', path: '/signup' },
  { key: 'unlock', name: '잠금 해제', path: '/unlock' },
  { key: 'vault', name: '볼트', path: '/vault' },
  { key: 'detail', name: '항목 상세', path: '/vault/github' },
  { key: 'generator', name: '비밀번호 생성기', path: '/generator' },
  { key: 'settings', name: '설정', path: '/settings' },
  { key: 'backup', name: '백업', path: '/settings/backup' },
  { key: 'password', name: '마스터 비밀번호 변경', path: '/settings/password' },
  { key: 'delete', name: '계정 삭제 확인', path: '/settings/delete' },
];
// 행 사이에 이동 화살표가 뜰 자리(labelGap)를 둔다
const MOCK = { phonePitch: 520, webPitch: 1400, originX: 0, labelY: 0, labelGap: 340, rowY: { i: 560, a: 1960, w: 3420 } };
const PLATFORM_NAME = { i: 'iPhone', a: 'Android', w: 'Web' };
// 한글 글리프가 있는 글꼴을 먼저 쓴다. 없으면 Inter로 내려간다
let UI_FONT = { family: 'Noto Sans KR', regular: 'Regular', medium: 'Medium', bold: 'Bold' };
let UI_MONO = { family: MONO, style: 'Regular' };
// 기기 몸체·브라우저 창 색은 화면 밖 하드웨어 색이라 토큰이 아닌 고정값을 쓴다
const HARDWARE = { body: '#1C1C1E', edge: '#3A3A3C', black: '#000000', chrome: '#E5E7EB', url: '#FFFFFF', dots: ['#FF5F57', '#FEBC2E', '#28C840'] };

async function loadMockFonts() {
  try {
    for (const s of ['Regular', 'Medium', 'Bold']) await figma.loadFontAsync({ family: 'Noto Sans KR', style: s });
  } catch (error) {
    UI_FONT = { family: SANS, regular: 'Regular', medium: 'Medium', bold: 'Bold' };
    for (const s of ['Regular', 'Medium', 'Bold']) await figma.loadFontAsync({ family: SANS, style: s });
    LOG.push('Noto Sans KR이 없어 목업 글꼴을 Inter로 그렸습니다(한글이 깨질 수 있음)');
  }
  await figma.loadFontAsync({ family: UI_MONO.family, style: UI_MONO.style });
}

// ---------- 그리기 도우미 (색은 모두 토큰 변수) ----------
// 지금 그리는 구역의 테마. 무료 플랜처럼 모드가 하나면 다크 구역은 'KeyManage Dark' 컬렉션 변수에 바인딩한다.
// 모드를 쓸 수 있으면 라이트와 같은 변수를 쓰고 구역의 프레임에 Dark 모드를 지정한다(applyDarkMode)
let THEME = 'light';
let SHIFT = 0;
const DARK_SHIFT = 4800;
const rowY = (p) => MOCK.rowY[p] + SHIFT;
function tok(key, opacity) {
  const name = `color/${key}`;
  const darkVar = THEME === 'dark' && DARK_COL ? ALL_VARS.find((v) => v.variableCollectionId === DARK_COL.id && v.name === name) : null;
  const p = darkVar ? figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', darkVar) : paint(name);
  return opacity == null ? p : Object.assign({}, p, { opacity });
}
function fill(node, key, opacity) { node.fills = [tok(key, opacity)]; }
function stroke(node, key, opacity, weight) { node.strokes = [tok(key, opacity)]; node.strokeWeight = weight || 1; }
function al(dir, gap) {
  const f = figma.createFrame();
  f.layoutMode = dir;
  f.primaryAxisSizingMode = 'AUTO';
  f.counterAxisSizingMode = 'AUTO';
  f.itemSpacing = gap || 0;
  f.fills = [];
  f.clipsContent = false;
  return f;
}
const col = (gap) => al('VERTICAL', gap);
function row(gap) { const f = al('HORIZONTAL', gap); f.counterAxisAlignItems = 'CENTER'; return f; }
function add(parent, child, fillWidth) {
  parent.appendChild(child);
  if (fillWidth) child.layoutSizingHorizontal = 'FILL';
  return child;
}
function pad(f, h, v) { f.paddingLeft = h; f.paddingRight = h; f.paddingTop = v; f.paddingBottom = v; }
function txt(chars, size, weight, color, width, mono) {
  const t = figma.createText();
  t.fontName = mono ? { family: UI_MONO.family, style: UI_MONO.style } : { family: UI_FONT.family, style: UI_FONT[weight || 'regular'] };
  t.characters = chars;
  t.fontSize = size;
  fill(t, color || 'foreground');
  if (width) { t.resize(width, t.height); t.textAutoResize = 'HEIGHT'; }
  return t;
}
function fixedRow(height, gap) { const r = row(gap); r.resize(10, height); r.counterAxisSizingMode = 'FIXED'; return r; }
function solid(hex) { return solidPaint(hex); }
function rect(w, h, r, hex, x, y, name) {
  const n = figma.createRectangle();
  n.resize(w, h); n.cornerRadius = r; n.fills = [solid(hex)]; n.x = x; n.y = y; n.name = name;
  return n;
}
function chevron(color, direction) {
  const v = figma.createVector();
  v.vectorPaths = [{ windingRule: 'NONZERO', data: direction === 'left' ? 'M 8 0 L 0 8 L 8 16' : 'M 0 0 L 8 8 L 0 16' }];
  v.fills = [];
  stroke(v, color, null, 2.5);
  v.strokeCap = 'ROUND';
  v.strokeJoin = 'ROUND';
  return v;
}

// Android 줄이 쓰는 Material Icons 24px 경로(Apache-2.0, google/material-design-icons).
// 앱의 KMIcons(Apps/Android/core/designsystem)와 같은 원본이다. 한쪽을 바꾸면 다른 쪽도 맞춘다
const MATERIAL_ICONS = {
  arrow_back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  lock: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z',
  visibility: 'M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z',
  content_copy: 'M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
  vpn_key: 'M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z',
  settings: 'M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z',
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
};
function mIcon(name, color, size) {
  const f = figma.createNodeFromSvg(`<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="${MATERIAL_ICONS[name]}"/></svg>`);
  f.name = 'Icon · ' + name;
  f.fills = [];
  for (const v of f.findAll((n) => n.type === 'VECTOR')) fill(v, color || 'foreground');
  if (size && size !== 24) f.rescale(size / 24);
  return f;
}
// M3 아이콘 버튼. 터치 영역 48, 아이콘 24
function mIconButton(name, color) {
  const b = fixedRow(48, 0); b.resize(48, 48); b.primaryAxisSizingMode = 'FIXED'; b.primaryAxisAlignItems = 'CENTER';
  b.name = 'Icon button · ' + name;
  b.appendChild(mIcon(name, color));
  return b;
}
// M3 Filled text field. 이름표는 칸 안에 있고, 값이 있으면 위로 떠올라 작아진다.
// 비밀번호 칸은 뒤쪽에 보기 아이콘 버튼을 둔다(o.trailing으로 바꿀 수 있다)
function m3Field(label, value, o) {
  const secret = value.startsWith('•');
  const trailing = o.trailing || (secret ? ['visibility'] : []);
  const box = fixedRow(56, 0);
  box.name = 'Input · ' + label;
  box.paddingLeft = 16; box.paddingRight = trailing.length ? 0 : 16;
  box.topLeftRadius = 4; box.topRightRadius = 4;
  fill(box, 'muted');
  stroke(box, o.focus ? 'primary' : 'muted-foreground');
  box.strokeTopWeight = 0; box.strokeLeftWeight = 0; box.strokeRightWeight = 0; box.strokeBottomWeight = o.focus ? 2 : 1;
  const texts = col(0);
  if (o.placeholder) texts.appendChild(txt(label, 16, 'regular', 'muted-foreground'));
  else {
    texts.appendChild(txt(label, 12, 'regular', o.focus ? 'primary' : 'muted-foreground'));
    texts.appendChild(txt(value, 16, 'regular', 'foreground', null, secret || o.mono));
  }
  box.appendChild(texts); texts.layoutGrow = 1;
  for (const name of trailing) box.appendChild(mIconButton(name, 'muted-foreground'));
  return box;
}

// ---------- 공용 요소 (플랫폼마다 모양만 다르다) ----------
function input(p, label, value, opts) {
  const o = opts || {};
  if (p === 'a') return m3Field(label, value, o);
  const wrap = col(6);
  wrap.appendChild(txt(label, p === 'i' ? 13 : 14, 'medium', p === 'w' ? 'foreground' : 'muted-foreground'));
  const box = fixedRow(o.height || (p === 'i' ? 48 : 40), 8);
  box.name = 'Input · ' + label;
  if (o.height) { box.counterAxisAlignItems = 'MIN'; box.paddingTop = 10; }
  if (p === 'i') { box.paddingLeft = 14; box.cornerRadius = 14; fill(box, 'muted'); if (o.focus) stroke(box, 'ring', 0.7, 1.5); }
  else { box.paddingLeft = 12; box.cornerRadius = TOKEN_RADIUS.md; fill(box, 'background'); stroke(box, o.focus ? 'ring' : 'input', null, o.focus ? 2 : 1); }
  const secret = value.startsWith('•');
  box.appendChild(txt(value, 16, 'regular', o.placeholder ? 'muted-foreground' : 'foreground', null, secret || o.mono));
  add(wrap, box, true);
  return wrap;
}
function button(p, label, kind) {
  const k = kind || 'primary';
  const b = fixedRow(p === 'i' ? 50 : 40, 0);
  b.name = 'Button · ' + label;
  b.primaryAxisAlignItems = 'CENTER';
  b.cornerRadius = p === 'i' ? 25 : p === 'a' ? 20 : TOKEN_RADIUS.md;
  b.paddingLeft = p === 'a' ? 24 : 16; b.paddingRight = b.paddingLeft;
  const size = p === 'i' ? 17 : 14;
  const weight = p === 'i' ? 'bold' : 'medium';
  if (k === 'primary') { fill(b, 'primary'); b.appendChild(txt(label, size, weight, 'primary-foreground')); }
  else if (k === 'destructive') { fill(b, 'destructive'); b.appendChild(txt(label, size, weight, 'destructive-foreground')); }
  else {
    if (p === 'i') fill(b, 'muted'); else { b.fills = []; stroke(b, p === 'a' ? 'muted-foreground' : 'input'); }
    b.appendChild(txt(label, size, weight, 'foreground'));
  }
  return b;
}
function link(p, label, color) {
  const r = row(0);
  r.primaryAxisAlignItems = 'CENTER';
  r.appendChild(txt(label, p === 'i' ? 15 : 14, 'medium', color || 'foreground'));
  return r;
}
function group(p, title, rows) {
  const g = col(8);
  if (title) g.appendChild(txt(title, 13, p === 'a' ? 'bold' : 'medium', 'muted-foreground'));
  const box = col(0);
  add(g, box, true);
  if (p === 'i') { box.cornerRadius = 12; fill(box, 'muted'); }
  if (p === 'w') { box.cornerRadius = TOKEN_RADIUS.lg; fill(box, 'card'); stroke(box, 'border'); }
  rows.forEach(([label, value, color, chevronRight], idx) => {
    const line = add(box, row(8), true);
    line.primaryAxisAlignItems = 'SPACE_BETWEEN';
    line.paddingLeft = p === 'a' ? 0 : 16; line.paddingRight = line.paddingLeft;
    line.paddingTop = p === 'a' ? 14 : p === 'i' ? 12 : 11; line.paddingBottom = line.paddingTop;
    if (idx > 0 && p !== 'a') { stroke(line, 'border'); line.strokeTopWeight = 1; line.strokeBottomWeight = 0; line.strokeLeftWeight = 0; line.strokeRightWeight = 0; }
    line.appendChild(txt(label, p === 'i' ? 17 : p === 'a' ? 16 : 14, 'regular', color || 'foreground'));
    const right = row(8);
    if (value) right.appendChild(txt(value, p === 'i' ? 17 : 14, 'regular', 'muted-foreground'));
    if (chevronRight && p === 'i') right.appendChild(chevron('muted-foreground', 'right'));
    line.appendChild(right);
  });
  return g;
}
function toggle(p, on) {
  const f = figma.createFrame();
  f.name = 'Switch';
  const knob = figma.createEllipse();
  if (p === 'i') { f.resize(51, 31); f.cornerRadius = 15.5; fill(f, on ? 'accent' : 'muted'); knob.resize(27, 27); knob.x = on ? 22 : 2; knob.y = 2; fill(knob, 'background'); }
  else if (p === 'a') { f.resize(52, 32); f.cornerRadius = 16; fill(f, on ? 'primary' : 'muted'); knob.resize(24, 24); knob.x = on ? 24 : 4; knob.y = 4; fill(knob, on ? 'primary-foreground' : 'muted-foreground'); }
  else { f.resize(18, 18); f.cornerRadius = 4; fill(f, on ? 'primary' : 'background'); if (!on) stroke(f, 'input'); knob.resize(8, 8); knob.x = 5; knob.y = 5; fill(knob, 'primary-foreground'); knob.visible = on; }
  f.appendChild(knob);
  return f;
}
function slider(width, ratio) {
  const f = figma.createFrame();
  f.name = 'Slider';
  f.resize(width, 24);
  f.fills = [];
  const track = figma.createRectangle(); track.resize(width, 4); track.y = 10; track.cornerRadius = 2; fill(track, 'muted');
  const active = figma.createRectangle(); active.resize(Math.round(width * ratio), 4); active.y = 10; active.cornerRadius = 2; fill(active, 'primary');
  const knob = figma.createEllipse(); knob.resize(24, 24); knob.x = Math.round(width * ratio) - 12; fill(knob, 'primary'); stroke(knob, 'background', null, 3);
  f.appendChild(track); f.appendChild(active); f.appendChild(knob);
  return f;
}
// EntropyGauge 계약: 취약(destructive) · 보통(primary) · 초강력(accent)
function gauge(level, label) {
  const spec = { weak: ['취약함', 'destructive', 0.35], medium: ['보통', 'primary', 0.65], strong: ['초강력', 'accent', 1] }[level];
  const g = col(6);
  g.name = 'EntropyGauge · ' + spec[0];
  const head = add(g, row(0), true);
  head.primaryAxisAlignItems = 'SPACE_BETWEEN';
  head.appendChild(txt(label || '비밀번호 강도', 12, 'medium', 'muted-foreground'));
  head.appendChild(txt(spec[0], 12, 'bold', spec[1]));
  const track = figma.createFrame(); track.name = 'Track'; track.resize(300, 6); track.cornerRadius = 3; fill(track, 'muted');
  track.layoutMode = 'HORIZONTAL'; track.primaryAxisSizingMode = 'FIXED'; track.counterAxisSizingMode = 'FIXED';
  const bar = figma.createRectangle(); bar.resize(Math.round(300 * spec[2]), 6); bar.cornerRadius = 3; fill(bar, spec[1]);
  track.appendChild(bar);
  add(g, track, true);
  return g;
}
function secretField(p) {
  const box = fixedRow(p === 'i' ? 48 : 40, 8);
  box.name = 'PasswordInputField';
  box.primaryAxisAlignItems = 'SPACE_BETWEEN';
  box.paddingLeft = 14; box.paddingRight = 14;
  box.cornerRadius = p === 'i' ? 14 : TOKEN_RADIUS.md;
  fill(box, p === 'w' ? 'background' : 'muted'); if (p === 'w') stroke(box, 'input');
  box.appendChild(txt('••••••••••••••••', 16, 'regular', 'foreground', null, true));
  box.appendChild(txt('보기', 13, 'medium', 'muted-foreground'));
  return box;
}
function burnBadge() {
  const b = row(6); b.name = 'BurnTimerBadge';
  pad(b, 10, 4); b.cornerRadius = 12; fill(b, 'destructive', 0.12);
  b.appendChild(txt('클립보드 삭제까지 28초', 12, 'bold', 'destructive', null, true));
  return b;
}
function lockIcon() {
  const c = col(-10);
  c.counterAxisAlignItems = 'CENTER';
  const shackle = figma.createEllipse(); shackle.resize(26, 30); shackle.fills = []; stroke(shackle, 'primary', null, 4);
  const body = figma.createRectangle(); body.resize(40, 30); body.cornerRadius = 8; fill(body, 'primary');
  c.appendChild(shackle); c.appendChild(body);
  return c;
}

// ---------- 플랫폼 셸 ----------
function statusBar(parent, p) {
  const sb = add(parent, row(0), true);
  sb.name = 'Status bar';
  sb.paddingLeft = p === 'i' ? 32 : 24; sb.paddingRight = sb.paddingLeft;
  sb.paddingTop = p === 'i' ? 18 : 12; sb.paddingBottom = 6;
  sb.primaryAxisAlignItems = 'SPACE_BETWEEN';
  sb.appendChild(txt('9:41', p === 'i' ? 16 : 14, p === 'i' ? 'bold' : 'medium'));
  sb.appendChild(txt(p === 'i' ? 'LTE' : '5G', 13, 'bold'));
}
const TABS = ['볼트', '생성기', '설정'];
const TAB_ICONS = { '볼트': 'lock', '생성기': 'vpn_key', '설정': 'settings' };
function tabBar(parent, active) {
  const wrap = add(parent, row(0), true);
  wrap.paddingLeft = 20; wrap.paddingRight = 20; wrap.paddingBottom = 28; wrap.paddingTop = 8;
  const tab = add(wrap, row(0), true);
  tab.name = 'Tab bar';
  tab.primaryAxisAlignItems = 'SPACE_BETWEEN';
  pad(tab, 28, 12);
  tab.cornerRadius = 32;
  fill(tab, 'muted', 0.9); stroke(tab, 'border');
  for (const l of TABS) tab.appendChild(txt(l, 12, l === active ? 'bold' : 'medium', l === active ? 'foreground' : 'muted-foreground'));
}
function navBar(parent, active) {
  const nav = fixedRow(80, 0);
  add(parent, nav, true);
  nav.name = 'Navigation bar';
  nav.primaryAxisAlignItems = 'SPACE_BETWEEN';
  nav.paddingLeft = 36; nav.paddingRight = 36;
  fill(nav, 'card');
  for (const l of TABS) {
    const on = l === active;
    const it = col(4); it.counterAxisAlignItems = 'CENTER';
    const pill = fixedRow(32, 0); pill.resize(64, 32); pill.primaryAxisSizingMode = 'FIXED'; pill.primaryAxisAlignItems = 'CENTER'; pill.cornerRadius = 16;
    if (on) fill(pill, 'muted');
    pill.appendChild(mIcon(TAB_ICONS[l], on ? 'foreground' : 'muted-foreground')); it.appendChild(pill);
    it.appendChild(txt(l, 12, on ? 'bold' : 'medium', on ? 'foreground' : 'muted-foreground'));
    nav.appendChild(it);
  }
}
function wrapDevice(page, screen, p, name) {
  const bezel = p === 'i' ? 14 : 10;
  const device = figma.createFrame();
  device.name = 'DS · Device · ' + name;
  device.resize(screen.width + bezel * 2, screen.height + bezel * 2);
  device.x = screen.x - bezel; device.y = screen.y - bezel;
  device.cornerRadius = screen.cornerRadius + bezel;
  device.fills = [solid(HARDWARE.body)];
  device.strokes = [solid(HARDWARE.edge)]; device.strokeWeight = 2;
  device.clipsContent = false;
  page.appendChild(device);
  device.appendChild(screen);
  screen.x = bezel; screen.y = bezel;
  const W = device.width;
  if (p === 'i') {
    device.appendChild(rect(124, 36, 18, HARDWARE.black, (W - 124) / 2, bezel + 11, 'Dynamic Island'));
    device.appendChild(rect(134, 5, 3, HARDWARE.body, (W - 134) / 2, bezel + screen.height - 13, 'Home indicator'));
  } else {
    const cam = figma.createEllipse(); cam.resize(14, 14); cam.fills = [solid(HARDWARE.black)]; cam.x = (W - 14) / 2; cam.y = bezel + 12; cam.name = 'Punch-hole camera';
    device.appendChild(cam);
    device.appendChild(rect(108, 4, 2, HARDWARE.body, (W - 108) / 2, bezel + screen.height - 10, 'Gesture bar'));
  }
  return device;
}
function wrapBrowser(page, screen, name, path) {
  const bar = 40;
  const win = figma.createFrame();
  win.name = 'DS · Browser · ' + name;
  win.resize(screen.width, screen.height + bar);
  win.x = screen.x; win.y = screen.y - bar;
  win.cornerRadius = 12; win.clipsContent = true;
  win.fills = [solid(HARDWARE.chrome)];
  win.strokes = [solid(HARDWARE.edge)]; win.strokeWeight = 1;
  page.appendChild(win);
  HARDWARE.dots.forEach((hex, i) => { const d = figma.createEllipse(); d.resize(12, 12); d.fills = [solid(hex)]; d.x = 16 + i * 20; d.y = 14; win.appendChild(d); });
  const url = rect(520, 26, 13, HARDWARE.url, (screen.width - 520) / 2, 7, 'Address bar');
  win.appendChild(url);
  const u = txt('localhost:8443' + path, 12, 'regular', 'muted-foreground');
  u.x = url.x + 16; u.y = url.y + 5; win.appendChild(u);
  win.appendChild(screen);
  screen.x = 0; screen.y = bar; screen.cornerRadius = 0;
  return win;
}

// o: { title, back, action, tab, center, bottom }
function phone(page, p, s, index, o) {
  const f = figma.createFrame();
  f.name = `${PLATFORM_NAME[p]} · ${s.name}`;
  f.resize(p === 'i' ? 402 : 412, p === 'i' ? 874 : 915);
  f.x = MOCK.originX + index * MOCK.phonePitch;
  f.y = rowY(p);
  fill(f, 'background');
  f.cornerRadius = p === 'i' ? 56 : 32;
  f.clipsContent = true;
  f.layoutMode = 'VERTICAL';
  f.primaryAxisSizingMode = 'FIXED';
  f.counterAxisSizingMode = 'FIXED';
  page.appendChild(f);
  wrapDevice(page, f, p, f.name);
  statusBar(f, p);
  if (p === 'i' && o.back) {
    const nb = add(f, row(6), true); nb.paddingLeft = 16; nb.paddingTop = 4; nb.paddingBottom = 4;
    nb.appendChild(chevron('foreground', 'left')); nb.appendChild(txt(o.back, 17, 'regular', 'foreground'));
  }
  if (p === 'a' && (o.title || o.back)) {
    // M3 small TopAppBar: 높이 64, 뒤로 가기는 48 아이콘 버튼, 제목은 왼쪽 정렬 22
    const bar = fixedRow(64, 4); add(f, bar, true); bar.name = 'Top app bar';
    bar.paddingLeft = o.back ? 4 : 16; bar.paddingRight = 4;
    if (o.back) bar.appendChild(mIconButton('arrow_back', 'foreground'));
    const title = txt(o.title || '', 22, 'regular'); bar.appendChild(title); title.layoutGrow = 1;
    if (o.action) { const act = row(0); pad(act, 12, 10); act.appendChild(txt(o.action, 14, 'medium', 'primary')); bar.appendChild(act); }
  }
  const body = add(f, col(16), true);
  body.name = 'Body';
  body.paddingLeft = 16; body.paddingRight = 16; body.paddingTop = 8; body.paddingBottom = 16;
  body.layoutGrow = 1;
  if (o.center) body.primaryAxisAlignItems = 'CENTER';
  if (o.bottom) body.primaryAxisAlignItems = 'MAX';
  if (p === 'i' && o.title) body.appendChild(txt(o.title, 34, 'bold'));
  if (p === 'i' && o.tab) tabBar(f, o.tab);
  if (p === 'a' && o.tab) navBar(f, o.tab);
  return body;
}
function screen3(page, key, phoneOpts, _webOpts, build) {
  const index = FLOW_STEPS.findIndex((s) => s.key === key);
  const s = FLOW_STEPS[index];
  for (const p of ['i', 'a']) build(p, phone(page, p, s, index, typeof phoneOpts === 'function' ? phoneOpts(p) : phoneOpts));
  WEB_SCREENS[key](page, s, index);
}
function brand(p, parent) { parent.appendChild(txt('KeyManage', p === 'w' ? 16 : 15, 'bold', 'foreground')); }

// ---------- 8단계 화면 ----------
// ---------- Web 목업: 실제 웹 화면(Apps/Web/src/ui)을 그대로 옮긴다 ----------
// 웹은 구현이 기준이고 목업이 따라간다. 문구는 Apps/Web/src/ui/messages와 정책 문구(SEC-001·005, COM-001)와 같다.
// iPhone·Android는 반대로 이 파일의 목업이 시안 기준이다(docs/design-system.md 5.2절).
const WEB_TEXT = {
  setupTitle: '마스터 비밀번호 설정',
  setupBody: '볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다.',
  deleteTitle: '계정 삭제',
  deleteBody: '계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요.',
  deletePhrase: "확인을 위해 '계정 삭제'를 입력해 주세요.",
  backupExportBody: '이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다.',
  backupRestoreBody: '백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다.',
  passwordChangeBody: '비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다.',
};
const WEB_ITEMS = [['GitHub', 'dev@example.com'], ['Google', 'dev@gmail.com'], ['AWS 콘솔', 'admin'], ['Notion', 'dev@example.com'], ['은행', null]];

function webButton(label, kind, size) {
  const sm = size === 'sm';
  const b = fixedRow(sm ? 32 : 40, 6);
  b.name = 'Button · ' + label;
  b.primaryAxisAlignItems = 'CENTER';
  b.cornerRadius = TOKEN_RADIUS.md;
  b.paddingLeft = sm ? 12 : 16; b.paddingRight = b.paddingLeft;
  const color = { primary: 'primary-foreground', destructive: 'destructive-foreground', outline: 'foreground', ghost: 'muted-foreground' }[kind || 'primary'];
  if (kind === 'outline') { b.fills = []; stroke(b, 'input'); }
  else if (kind === 'ghost') b.fills = [];
  else fill(b, kind || 'primary');
  b.appendChild(txt(label, sm ? 12 : 14, 'medium', color));
  return b;
}
function webField(label, value, opts) {
  const o = opts || {};
  const wrap = col(8);
  wrap.appendChild(txt(label, 14, 'medium'));
  const box = fixedRow(o.height || 40, 8);
  box.name = 'Input · ' + label;
  box.primaryAxisAlignItems = 'SPACE_BETWEEN';
  box.paddingLeft = 12; box.paddingRight = 12; box.cornerRadius = TOKEN_RADIUS.md;
  fill(box, 'background'); stroke(box, 'input');
  if (o.height) { box.counterAxisAlignItems = 'MIN'; box.paddingTop = 10; }
  box.appendChild(txt(value, 14, 'regular', o.placeholder ? 'muted-foreground' : 'foreground', null, o.secret));
  if (o.secret) box.appendChild(txt('보기', 12, 'medium', 'muted-foreground'));
  add(wrap, box, true);
  return wrap;
}
function webAlert(title, body) {
  const a = col(4); a.name = 'Alert · info';
  pad(a, 16, 12); a.cornerRadius = TOKEN_RADIUS.lg; fill(a, 'muted'); stroke(a, 'border');
  if (title) a.appendChild(txt(title, 14, 'bold'));
  add(a, txt(body, 14, 'regular', 'muted-foreground', 300), true);
  return a;
}
function webCard(gap) {
  const c = col(gap == null ? 16 : gap);
  c.cornerRadius = TOKEN_RADIUS.lg; fill(c, 'card'); stroke(c, 'border');
  return c;
}
function webFrame(page, s, index) {
  const f = figma.createFrame();
  f.name = `Web · ${s.name}`;
  f.resize(1280, 800);
  f.x = MOCK.originX + index * MOCK.webPitch;
  f.y = rowY('w');
  fill(f, 'background');
  f.cornerRadius = 12;
  f.clipsContent = true;
  page.appendChild(f);
  wrapBrowser(page, f, f.name, s.path);
  return f;
}
// 인증 화면: 가운데 카드(max-w-sm 384px), 머리에 'KeyManage'와 작은 부제 (AuthScreen·UnlockScreen)
function webAuth(page, s, index, subtitle) {
  const f = webFrame(page, s, index);
  f.layoutMode = 'VERTICAL'; f.primaryAxisSizingMode = 'FIXED'; f.counterAxisSizingMode = 'FIXED';
  f.primaryAxisAlignItems = 'CENTER'; f.counterAxisAlignItems = 'CENTER';
  const card = webCard(16); card.name = 'Card'; f.appendChild(card);
  card.resize(384, 10); card.counterAxisSizingMode = 'FIXED'; card.primaryAxisSizingMode = 'AUTO';
  pad(card, 24, 24);
  if (subtitle != null) {
    const head = col(4); add(card, head, true);
    head.appendChild(txt('KeyManage', 24, 'bold'));
    head.appendChild(txt(subtitle, 14, 'regular', 'muted-foreground'));
  }
  return card;
}
// 앱 화면: 사이드바(240px) + 메인 (VaultHomeScreen). 사이드바 하단에 자동 잠금·백업·비밀번호 변경·계정 삭제·잠그기/로그아웃
function webApp(page, s, index, active) {
  const f = webFrame(page, s, index);
  f.layoutMode = 'HORIZONTAL'; f.primaryAxisSizingMode = 'FIXED'; f.counterAxisSizingMode = 'FIXED';
  const side = col(16); side.name = 'Sidebar'; f.appendChild(side);
  side.resize(240, 800); side.primaryAxisSizingMode = 'FIXED'; side.counterAxisSizingMode = 'FIXED';
  side.primaryAxisAlignItems = 'SPACE_BETWEEN';
  pad(side, 16, 16);
  fill(side, 'card'); stroke(side, 'border'); side.strokeTopWeight = 0; side.strokeLeftWeight = 0; side.strokeBottomWeight = 0;
  const top = col(16); add(side, top, true);
  const brandBox = col(2); add(top, brandBox, true);
  brandBox.appendChild(txt('KeyManage', 18, 'bold'));
  brandBox.appendChild(txt('dev@example.com', 12, 'regular', 'muted-foreground'));
  const nav = col(4); add(top, nav, true);
  for (const l of ['볼트', '생성기', '설정']) {
    const on = l === active;
    const it = add(nav, row(12), true); pad(it, 12, 8); it.cornerRadius = TOKEN_RADIUS.md;
    if (on) fill(it, 'accent');
    const icon = figma.createRectangle(); icon.resize(16, 16); icon.cornerRadius = 4; fill(icon, on ? 'accent-foreground' : 'muted-foreground', 0.8);
    it.appendChild(icon);
    it.appendChild(txt(l, 14, on ? 'bold' : 'medium', on ? 'accent-foreground' : 'muted-foreground'));
  }
  const foot = col(12); add(side, foot, true);
  stroke(foot, 'border'); foot.strokeTopWeight = 1; foot.strokeBottomWeight = 0; foot.strokeLeftWeight = 0; foot.strokeRightWeight = 0;
  foot.paddingTop = 12;
  const box = col(8); add(foot, box, true);
  pad(box, 12, 12); box.cornerRadius = TOKEN_RADIUS.lg; fill(box, 'muted', 0.3); stroke(box, 'border', 0.5);
  const lockRow = add(box, row(8), true); lockRow.primaryAxisAlignItems = 'SPACE_BETWEEN';
  lockRow.appendChild(txt('자동 잠금', 12, 'regular', 'muted-foreground'));
  const sel = row(4); pad(sel, 8, 4); sel.cornerRadius = TOKEN_RADIUS.md; fill(sel, 'background'); stroke(sel, 'input');
  sel.appendChild(txt('5분', 12, 'regular')); lockRow.appendChild(sel);
  for (const [l, kind] of [['백업 내보내기', 'outline'], ['백업에서 복원', 'outline'], ['마스터 비밀번호 변경', 'outline'], ['계정 삭제', 'danger']]) {
    const b = add(box, fixedRow(28, 6), true); b.paddingLeft = 8; b.cornerRadius = TOKEN_RADIUS.md; b.fills = [];
    stroke(b, kind === 'danger' ? 'destructive' : 'input', kind === 'danger' ? 0.4 : null);
    b.appendChild(txt(l, 12, 'regular', kind === 'danger' ? 'destructive' : 'foreground'));
  }
  const acts = add(foot, row(8), true);
  add(acts, webButton('잠그기', 'outline', 'sm')).layoutGrow = 1;
  add(acts, webButton('로그아웃', 'ghost', 'sm')).layoutGrow = 1;
  const main = col(16); main.name = 'Main'; f.appendChild(main);
  main.layoutGrow = 1; main.layoutSizingVertical = 'FILL';
  pad(main, 24, 24);
  return { frame: f, main };
}
// 볼트 탭: 왼쪽 목록(384px) + 오른쪽 상세 (VaultScreen split)
function webVaultList(main, selected) {
  const split = add(main, row(24), true); split.counterAxisAlignItems = 'MIN'; split.layoutGrow = 1;
  const list = col(12); list.name = 'ItemList'; split.appendChild(list);
  list.resize(384, 10); list.counterAxisSizingMode = 'FIXED';
  const top = add(list, row(8), true); top.counterAxisAlignItems = 'MAX';
  add(top, webField('항목 검색', '', { placeholder: true })).layoutGrow = 1;
  top.appendChild(webButton('항목 추가'));
  const items = col(8); add(list, items, true);
  for (const [name, user] of WEB_ITEMS) {
    const card = add(items, webCard(2), true);
    card.name = 'VaultItem · ' + name;
    const it = add(card, row(8), true); pad(it, 16, 12); it.primaryAxisAlignItems = 'SPACE_BETWEEN';
    if (name === selected) stroke(card, 'ring', null, 2);
    const texts = col(2); it.appendChild(texts);
    texts.appendChild(txt(name, 14, 'medium'));
    if (user) texts.appendChild(txt(user, 12, 'regular', 'muted-foreground'));
    it.appendChild(txt('복사', 12, 'medium', 'muted-foreground'));
  }
  const detail = col(16); detail.name = 'Detail'; split.appendChild(detail);
  detail.layoutGrow = 1;
  return detail;
}
function webEmptyDetail(detail) {
  const box = add(detail, webCard(4), true);
  pad(box, 24, 48); box.counterAxisAlignItems = 'CENTER';
  box.appendChild(txt('선택된 항목이 없습니다', 14, 'medium'));
  box.appendChild(txt('목록에서 항목을 선택하거나 새 항목을 추가해 주세요.', 12, 'regular', 'muted-foreground'));
}
function webDetailField(parent, label, value, opts) {
  const o = opts || {};
  const r = add(parent, row(8), true); r.primaryAxisAlignItems = 'SPACE_BETWEEN';
  const t = col(4); r.appendChild(t);
  t.appendChild(txt(label, 12, 'medium', 'muted-foreground'));
  t.appendChild(txt(value, 14, 'regular', 'foreground', null, o.secret));
  const acts = row(8); r.appendChild(acts);
  for (const a of o.actions || []) acts.appendChild(webButton(a, 'outline', 'sm'));
}

const WEB_SCREENS = {
  login(page, s, i) {
    const c = webAuth(page, s, i, '볼트에 로그인');
    add(c, webField('이메일', 'dev@example.com'), true);
    add(c, webField('마스터 비밀번호', '••••••••••••', { secret: true }), true);
    add(c, webButton('로그인'), true);
    add(c, webButton('계정 만들기', 'ghost'), true);
  },
  signup(page, s, i) {
    const c = webAuth(page, s, i, '새 계정 만들기');
    add(c, webField('이메일', 'dev@example.com'), true);
    add(c, webAlert(WEB_TEXT.setupTitle, WEB_TEXT.setupBody), true);
    add(c, webField('마스터 비밀번호', '••••••••••••••••', { secret: true }), true);
    add(c, gauge('strong'), true);
    add(c, webField('마스터 비밀번호 확인', '••••••••••••••••', { secret: true }), true);
    add(c, webButton('가입'), true);
    add(c, webButton('이미 계정이 있어요', 'ghost'), true);
  },
  unlock(page, s, i) {
    const c = webAuth(page, s, i, null);
    const head = col(8); add(c, head, true); head.counterAxisAlignItems = 'CENTER';
    head.appendChild(lockIcon());
    head.appendChild(txt('볼트가 잠겼습니다', 20, 'bold'));
    head.appendChild(txt('dev@example.com', 14, 'regular', 'muted-foreground'));
    add(c, webField('마스터 비밀번호', '••••••••••••', { secret: true }), true);
    add(c, webButton('잠금 해제'), true);
    add(c, webButton('로그아웃', 'ghost'), true);
  },
  vault(page, s, i) {
    const { main } = webApp(page, s, i, '볼트');
    webEmptyDetail(webVaultList(main, null));
  },
  detail(page, s, i) {
    const { main } = webApp(page, s, i, '볼트');
    const detail = webVaultList(main, 'GitHub');
    const card = add(detail, webCard(16), true); pad(card, 24, 24);
    const head = add(card, row(8), true); head.primaryAxisAlignItems = 'SPACE_BETWEEN';
    head.appendChild(webButton('목록으로', 'ghost', 'sm'));
    const right = row(8); right.appendChild(webButton('수정', 'outline', 'sm')); right.appendChild(webButton('삭제', 'destructive', 'sm')); head.appendChild(right);
    card.appendChild(txt('GitHub', 20, 'bold'));
    webDetailField(card, '아이디', 'dev@example.com', { actions: ['복사'] });
    webDetailField(card, '비밀번호', '••••••••••••••••', { secret: true, actions: ['보기', '복사'] });
    webDetailField(card, 'URL', 'https://github.com');
    webDetailField(card, '메모', '2FA 복구 코드는 종이에 보관');
  },
  generator(page, s, i) {
    const { main } = webApp(page, s, i, '생성기');
    main.counterAxisAlignItems = 'CENTER';
    const card = webCard(16); main.appendChild(card);
    card.resize(576, 10); card.counterAxisSizingMode = 'FIXED'; pad(card, 24, 24);
    card.appendChild(txt('비밀번호 생성기', 20, 'bold'));
    const result = add(card, col(8), true); pad(result, 12, 12); result.cornerRadius = TOKEN_RADIUS.md; fill(result, 'muted');
    result.appendChild(txt('k7#Qv2!mR9$wLx4&Tz8p', 18, 'regular', 'foreground', null, true));
    add(card, gauge('strong', '생성된 비밀번호 강도'), true);
    const opts = add(card, row(16), true);
    const len = row(8); len.appendChild(txt('길이', 14, 'medium'));
    const num = row(0); pad(num, 10, 6); num.cornerRadius = TOKEN_RADIUS.md; fill(num, 'background'); stroke(num, 'input');
    num.appendChild(txt('20', 14, 'regular', 'foreground', null, true)); len.appendChild(num); opts.appendChild(len);
    for (const l of ['대문자', '소문자', '숫자', '특수기호']) { const c = row(6); c.appendChild(toggle('w', true)); c.appendChild(txt(l, 14, 'regular')); opts.appendChild(c); }
    const acts = add(card, row(8), true);
    acts.appendChild(webButton('다시 생성', 'outline')); acts.appendChild(webButton('복사'));
  },
  settings(page, s, i) {
    const { main } = webApp(page, s, i, '설정');
    main.counterAxisAlignItems = 'CENTER';
    const view = col(24); main.appendChild(view);
    view.resize(576, 10); view.counterAxisSizingMode = 'FIXED';
    const head = add(view, col(4), true);
    head.appendChild(txt('설정', 20, 'bold'));
    head.appendChild(txt('계정 및 보안 정책 상태를 확인합니다.', 14, 'regular', 'muted-foreground'));
    const sections = [
      ['계정', [['이메일', 'dev@example.com'], ['동기화 상태', '최신 상태 (동기화 완료)']]],
      ['보안 정책', [['클립보드 자동 삭제', '30초 (고정, COM-001)'], ['암호화 구조', '클라이언트 영지식 (AES-256-GCM)']]],
      ['서버', [['서버 주소', 'https://localhost:8443']]],
    ];
    for (const [title, rows] of sections) {
      const sec = add(view, col(8), true);
      sec.appendChild(txt(title, 14, 'bold'));
      const card = add(sec, webCard(0), true);
      rows.forEach(([l, v], idx) => {
        const r = add(card, row(16), true); pad(r, 16, 16); r.primaryAxisAlignItems = 'SPACE_BETWEEN';
        if (idx > 0) { stroke(r, 'border'); r.strokeTopWeight = 1; r.strokeBottomWeight = 0; r.strokeLeftWeight = 0; r.strokeRightWeight = 0; }
        r.appendChild(txt(l, 14, 'regular', 'muted-foreground')); r.appendChild(txt(v, 14, 'medium'));
      });
    }
    const ctrl = add(view, webCard(0), true); fill(ctrl, 'muted', 0.2);
    const cr = add(ctrl, row(12), true); pad(cr, 16, 16); cr.primaryAxisAlignItems = 'SPACE_BETWEEN';
    const ct = col(2); cr.appendChild(ct);
    ct.appendChild(txt('세션 제어', 14, 'medium'));
    ct.appendChild(txt('볼트를 잠그거나 계정에서 로그아웃합니다.', 12, 'regular', 'muted-foreground'));
    const cb = row(8); cr.appendChild(cb);
    cb.appendChild(webButton('잠그기', 'outline', 'sm')); cb.appendChild(webButton('로그아웃', 'destructive', 'sm'));
  },
  // 계정 삭제는 볼트 화면 위에 뜨는 모달이다 (DeleteAccountDialog, max-w-md 448px)
  delete(page, s, i) {
    const { frame, main } = webApp(page, s, i, '볼트');
    webEmptyDetail(webVaultList(main, null));
    const overlay = figma.createRectangle(); overlay.name = 'Overlay'; overlay.resize(1280, 800); fill(overlay, 'overlay');
    frame.appendChild(overlay); overlay.layoutPositioning = 'ABSOLUTE'; overlay.x = 0; overlay.y = 0;
    const dlg = webCard(16); dlg.name = 'ModalDialog · 계정 삭제';
    frame.appendChild(dlg); dlg.layoutPositioning = 'ABSOLUTE';
    dlg.resize(448, 10); dlg.counterAxisSizingMode = 'FIXED'; pad(dlg, 24, 24);
    dlg.appendChild(txt(WEB_TEXT.deleteTitle, 18, 'bold', 'destructive'));
    add(dlg, txt(WEB_TEXT.deleteBody, 14, 'regular', 'muted-foreground', 400), true);
    add(dlg, webField('마스터 비밀번호', '••••••••••••', { secret: true }), true);
    add(dlg, webField(WEB_TEXT.deletePhrase, '계정 삭제'), true);
    const acts = add(dlg, row(8), true);
    acts.appendChild(webButton('취소', 'outline', 'sm')); acts.appendChild(webButton('삭제', 'destructive', 'sm'));
    dlg.x = (1280 - 448) / 2; dlg.y = 160;
  },
};

// ---------- 8단계 화면 (iPhone·Android는 이 목업이 시안 기준) ----------
const MOCK_STEPS = {
  login(page) {
    screen3(page, 'login', { center: true }, { auth: true }, (p, b) => {
      brand(p, b);
      b.appendChild(txt('볼트에 로그인', p === 'i' ? 34 : p === 'a' ? 28 : 24, p === 'a' ? 'regular' : 'bold'));
      add(b, input(p, '이메일', 'dev@example.com'), true);
      add(b, input(p, '마스터 비밀번호', '••••••••••••', { focus: true }), true);
      add(b, button(p, '로그인'), true);
      add(b, link(p, '새 계정 만들기'), true);
    });
  },
  signup(page) {
    screen3(page, 'signup', (p) => ({ back: p === 'i' ? '로그인' : true, title: p === 'a' ? '계정 만들기' : null, center: p === 'i' }), { auth: true }, (p, b) => {
      if (p !== 'a') b.appendChild(txt('계정 만들기', p === 'i' ? 34 : 24, 'bold'));
      add(b, input(p, '이메일', 'dev@example.com'), true);
      add(b, input(p, '마스터 비밀번호', '••••••••••••••••'), true);
      add(b, input(p, '마스터 비밀번호 확인', '••••••••••••••••', { focus: true }), true);
      add(b, gauge('strong'), true);
      add(b, txt(WEB_TEXT.setupBody, 13, 'regular', 'muted-foreground', 336), true);
      add(b, button(p, '가입'), true);
      add(b, link(p, '이미 계정이 있어요'), true);
    });
  },
  unlock(page) {
    screen3(page, 'unlock', { center: true }, { auth: true }, (p, b) => {
      b.counterAxisAlignItems = 'CENTER';
      b.appendChild(p === 'a' ? mIcon('lock', 'primary', 48) : lockIcon());
      b.appendChild(txt('볼트가 잠겼습니다', p === 'i' ? 28 : p === 'a' ? 24 : 22, p === 'a' ? 'regular' : 'bold'));
      b.appendChild(txt('dev@example.com', 14, 'regular', 'muted-foreground'));
      add(b, input(p, '마스터 비밀번호', '••••••••••••', { focus: true }), true);
      add(b, button(p, '잠금 해제'), true);
      add(b, link(p, '로그아웃', 'muted-foreground'), true);
    });
  },
  vault(page) {
    const items = [['GitHub', 'dev@example.com'], ['Google', 'dev@gmail.com'], ['AWS 콘솔', 'admin'], ['Notion', 'dev@example.com'], ['은행', '(아이디 없음)']];
    screen3(page, 'vault', (p) => ({ title: '볼트', tab: '볼트', action: p === 'a' ? '잠금' : null }),
      { active: '볼트', title: '볼트', subtitle: '항목 5개 · 모든 값은 이 기기에서만 복호화됩니다.', width: 720 }, (p, b) => {
      const top = add(b, row(8), true);
      const search = add(top, input(p, '검색', '서비스 이름이나 아이디', { placeholder: true }), false);
      search.layoutGrow = 1;
      if (p === 'w') { top.counterAxisAlignItems = 'MAX'; top.appendChild(button(p, '항목 추가')); }
      const list = col(p === 'w' ? 0 : 8); add(b, list, true);
      if (p === 'w') { list.cornerRadius = TOKEN_RADIUS.lg; fill(list, 'card'); stroke(list, 'border'); }
      items.forEach(([name, user], idx) => {
        const it = add(list, row(12), true);
        it.name = 'VaultItem · ' + name;
        pad(it, 16, p === 'w' ? 12 : 14);
        if (p !== 'w') { it.cornerRadius = p === 'i' ? 14 : TOKEN_RADIUS.lg; fill(it, p === 'i' ? 'muted' : 'card'); if (p === 'a') stroke(it, 'border'); }
        else if (idx > 0) { stroke(it, 'border'); it.strokeTopWeight = 1; it.strokeBottomWeight = 0; it.strokeLeftWeight = 0; it.strokeRightWeight = 0; }
        const avatar = fixedRow(36, 0); avatar.resize(36, 36); avatar.primaryAxisSizingMode = 'FIXED'; avatar.primaryAxisAlignItems = 'CENTER';
        avatar.cornerRadius = 18; fill(avatar, 'background'); stroke(avatar, 'border');
        avatar.appendChild(txt(name.slice(0, 1), 14, 'bold'));
        it.appendChild(avatar);
        const texts = col(2); it.appendChild(texts); texts.layoutGrow = 1;
        texts.appendChild(txt(name, p === 'i' ? 17 : 15, 'medium'));
        texts.appendChild(txt(user, 13, 'regular', 'muted-foreground'));
        if (p === 'i') it.appendChild(chevron('muted-foreground', 'right'));
        else if (p === 'a') it.appendChild(mIconButton('content_copy', 'muted-foreground'));
        else it.appendChild(txt('복사', 13, 'medium', 'muted-foreground'));
      });
      if (p === 'a') {
        const fab = fixedRow(56, 0); fab.resize(56, 56); fab.primaryAxisSizingMode = 'FIXED'; fab.primaryAxisAlignItems = 'CENTER';
        fab.cornerRadius = 16; fill(fab, 'primary'); fab.appendChild(mIcon('add', 'primary-foreground'));
        const fr = add(b, row(0), true); fr.primaryAxisAlignItems = 'MAX'; fr.appendChild(fab);
      }
    });
  },
  detail(page) {
    screen3(page, 'detail', (p) => ({ back: p === 'i' ? '볼트' : true, title: 'GitHub' }),
      { active: '볼트', title: '항목 수정', subtitle: '서비스 이름과 아이디, 비밀번호를 고칩니다.' }, (p, b) => {
      if (p === 'w') {
        add(b, input(p, '서비스 이름', 'GitHub'), true);
        add(b, input(p, '아이디', 'dev@example.com'), true);
      } else {
        add(b, group(p, null, [['서비스 이름', 'GitHub'], ['아이디', 'dev@example.com']]), true);
      }
      if (p === 'a') add(b, input(p, '비밀번호', '••••••••••••••••', { trailing: ['visibility', 'content_copy'] }), true);
      else {
        const pw = col(6); add(b, pw, true);
        pw.appendChild(txt('비밀번호', 13, 'medium', p === 'w' ? 'foreground' : 'muted-foreground'));
        add(pw, secretField(p), true);
      }
      add(b, gauge('strong'), true);
      if (p === 'w') add(b, input(p, '메모', '2FA 복구 코드는 종이에 보관', { height: 72 }), true);
      else b.appendChild(burnBadge());
      const acts = add(b, row(8), true);
      if (p === 'w') {
        acts.primaryAxisAlignItems = 'SPACE_BETWEEN';
        acts.appendChild(button(p, '항목 삭제', 'destructive'));
        const right = row(8); right.appendChild(button(p, '취소', 'secondary')); right.appendChild(button(p, '저장')); acts.appendChild(right);
      } else {
        add(acts, button(p, '아이디 복사', 'secondary')).layoutGrow = 1;
        add(acts, button(p, '항목 삭제', 'destructive')).layoutGrow = 1;
      }
    });
  },
  generator(page) {
    screen3(page, 'generator', (p) => ({ title: p === 'a' ? '생성기' : '비밀번호 생성기', tab: '생성기' }),
      { active: '비밀번호 생성기', title: '비밀번호 생성기', subtitle: '생성은 이 기기 안에서만 이루어집니다.' }, (p, b) => {
      const result = col(12); add(b, result, true);
      pad(result, 16, 16); result.cornerRadius = p === 'i' ? 20 : TOKEN_RADIUS.lg;
      fill(result, p === 'i' ? 'muted' : 'card'); if (p !== 'i') stroke(result, 'border');
      result.appendChild(txt('k7#Qv2!mR9$wLx4&Tz8p', p === 'w' ? 22 : 20, 'regular', 'foreground', null, true));
      add(result, gauge('strong'), true);
      const len = add(b, row(0), true); len.primaryAxisAlignItems = 'SPACE_BETWEEN';
      len.appendChild(txt('길이', p === 'i' ? 17 : 15, 'medium')); len.appendChild(txt('20', p === 'i' ? 17 : 15, 'bold', 'foreground', null, true));
      b.appendChild(slider(p === 'w' ? 560 : p === 'i' ? 361 : 380, (20 - 8) / (64 - 8)));
      const opts = col(0); add(b, opts, true);
      if (p === 'i') { opts.cornerRadius = 12; fill(opts, 'muted'); }
      ['대문자', '소문자', '숫자', '특수기호'].forEach((l, idx) => {
        const r = add(opts, row(12), true);
        r.primaryAxisAlignItems = p === 'w' ? 'MIN' : 'SPACE_BETWEEN';
        r.paddingLeft = p === 'i' ? 16 : 0; r.paddingRight = r.paddingLeft; r.paddingTop = p === 'w' ? 6 : 10; r.paddingBottom = r.paddingTop;
        if (p === 'w') { r.appendChild(toggle(p, true)); r.appendChild(txt(l, 14, 'regular')); }
        else { r.appendChild(txt(l, p === 'i' ? 17 : 16, 'regular')); r.appendChild(toggle(p, true)); }
        if (idx > 0 && p === 'i') { stroke(r, 'border'); r.strokeTopWeight = 1; r.strokeBottomWeight = 0; r.strokeLeftWeight = 0; r.strokeRightWeight = 0; }
      });
      const acts = add(b, row(8), true);
      const a1 = add(acts, button(p, '다시 생성', 'secondary')); const a2 = add(acts, button(p, '복사'));
      if (p !== 'w') { a1.layoutGrow = 1; a2.layoutGrow = 1; }
    });
  },
  settings(page) {
    screen3(page, 'settings', { title: '설정', tab: '설정' },
      { active: '설정', title: '설정', subtitle: '계정 및 보안 정책 상태를 확인합니다.', width: 640 }, (p, b) => {
      b.itemSpacing = p === 'w' ? 20 : 18;
      add(b, group(p, '계정', [['이메일', 'dev@example.com']]), true);
      add(b, group(p, '보안', [['자동 잠금', '5분', null, true], ['클립보드 자동 삭제', '30초'], ['암호화 구조', 'AES-256-GCM']]), true);
      add(b, group(p, '백업', [['백업 내보내기', null, null, true], ['백업에서 복원', null, null, true]]), true);
      add(b, group(p, '세션 제어', [['마스터 비밀번호 변경', null, null, true], ['로그아웃'], ['계정 삭제', null, 'destructive']]), true);
    });
  },
  // VLT-004. 내보내기 안내와 복원(백업 비밀번호 입력)을 한 화면에 둔다
  backup(page) {
    screen3(page, 'backup', (p) => ({ back: p === 'i' ? '설정' : true, title: '백업' }),
      { active: '설정', title: '백업', subtitle: '볼트를 암호화된 파일로 내보내거나 그 파일에서 복원합니다.' }, (p, b) => {
      add(b, txt('백업 내보내기', p === 'i' ? 20 : 16, 'bold'), true);
      add(b, txt(WEB_TEXT.backupExportBody, 14, 'regular', 'muted-foreground', 336), true);
      add(b, txt('keymanage-backup-20261118.json', 13, 'regular', 'muted-foreground', null, true), true);
      add(b, button(p, '내보내기'), true);
      add(b, txt('백업에서 복원', p === 'i' ? 20 : 16, 'bold'), true);
      add(b, txt(WEB_TEXT.backupRestoreBody, 14, 'regular', 'muted-foreground', 336), true);
      add(b, button(p, '파일 선택', 'secondary'), true);
      add(b, input(p, '백업 파일의 마스터 비밀번호', '••••••••••••', { focus: true }), true);
      add(b, button(p, '복원'), true);
    });
  },
  // SEC-004. 변경 전 안내와 세 입력 칸
  password(page) {
    screen3(page, 'password', (p) => ({ back: p === 'i' ? '설정' : true, title: p === 'a' ? '비밀번호 변경' : null }),
      { active: '설정', title: '마스터 비밀번호 변경', subtitle: '모든 기기가 새 비밀번호로 다시 로그인합니다.' }, (p, b) => {
      if (p === 'i') b.appendChild(txt('마스터 비밀번호 변경', 28, 'bold'));
      add(b, txt(WEB_TEXT.passwordChangeBody, 14, 'regular', 'muted-foreground', 336), true);
      add(b, input(p, '현재 마스터 비밀번호', '••••••••••••'), true);
      add(b, input(p, '새 마스터 비밀번호', '••••••••••••••••'), true);
      add(b, input(p, '새 마스터 비밀번호 확인', '••••••••••••••••', { focus: true }), true);
      add(b, gauge('strong'), true);
      add(b, button(p, '변경'), true);
    });
  },
  delete(page) {
    screen3(page, 'delete', (p) => ({ bottom: p === 'i', center: p === 'a' }), { auth: true, width: 460, danger: true }, (p, b) => {
      let box = b;
      if (p === 'i') {
        box = col(14); add(b, box, true); pad(box, 20, 20); box.cornerRadius = 26; fill(box, 'card'); stroke(box, 'border');
        const grab = figma.createRectangle(); grab.resize(36, 5); grab.cornerRadius = 3; fill(grab, 'muted-foreground', 0.6);
        const gw = add(box, row(0), true); gw.primaryAxisAlignItems = 'CENTER'; gw.appendChild(grab);
      } else if (p === 'a') {
        // M3 AlertDialog: 설정 화면 위에 스크림을 깔고 모서리 28, 여백 24인 대화상자를 띄운다
        fill(b.parent, 'overlay');
        box = col(16); add(b, box, true); box.name = 'AlertDialog'; pad(box, 24, 24); box.cornerRadius = 28; fill(box, 'card');
      }
      box.appendChild(txt(WEB_TEXT.deleteTitle, p === 'a' ? 24 : 20, p === 'a' ? 'regular' : 'bold', 'destructive'));
      add(box, txt(WEB_TEXT.deleteBody, 14, 'regular', 'muted-foreground', 320), true);
      add(box, input(p, '마스터 비밀번호', '••••••••••••'), true);
      add(box, input(p, WEB_TEXT.deletePhrase, '계정 삭제', { focus: true }), true);
      const acts = add(box, row(8), true);
      if (p === 'i') { acts.layoutMode = 'VERTICAL'; add(acts, button(p, '삭제', 'destructive'), true); add(acts, button(p, '취소', 'secondary'), true); }
      else { acts.primaryAxisAlignItems = 'MAX'; acts.appendChild(button(p, '취소', 'secondary')); acts.appendChild(button(p, '삭제', 'destructive')); }
    });
  },
};

function mockLabels(page) {
  const t = (c, s, color, x, y) => { const n = txt(c, s, 'bold', color); n.name = 'DS · Label · ' + c; n.x = x; n.y = y; page.appendChild(n); };
  const theme = THEME === 'dark' ? '다크' : '라이트';
  t(`화면 흐름 · ${theme} · 로그인 → 계정 삭제`, 48, 'foreground', MOCK.originX, MOCK.labelY + SHIFT);
  t('화살표는 화면 사이 이동이다. 세 플랫폼 모두 같은 흐름이고 이동 수단만 다르다(iPhone 탭 바 · Android 내비게이션 바 · Web 사이드바).', 16, 'muted-foreground', MOCK.originX, MOCK.labelY + SHIFT + 70);
  for (const p of ['i', 'a', 'w']) {
    FLOW_STEPS.forEach((s, i) => t(`${i + 1} ${s.name}`, 24, 'foreground', screenBox(p, i).x, rowY(p) - MOCK.labelGap));
  }
  t('iPhone', 32, 'muted-foreground', -260, rowY('i'));
  t('Android', 32, 'muted-foreground', -260, rowY('a'));
  t('Web · 데스크톱', 32, 'muted-foreground', -260, rowY('w'));
}

// ---------- 화면 사이 이동 화살표 ----------
// [출발 단계, 도착 단계, 이동 이름]. 단계 번호는 FLOW_STEPS 순서(0부터)
const FLOW_LINKS = [
  ['login', 'signup', '새 계정 만들기'],
  ['login', 'vault', '로그인 성공'],
  ['signup', 'vault', '가입 완료'],
  ['unlock', 'vault', '잠금 해제'],
  ['vault', 'detail', '항목 선택'],
  ['vault', 'generator', '생성기 탭'],
  ['vault', 'settings', '설정 탭'],
  ['settings', 'backup', '백업'],
  ['settings', 'password', '비밀번호 변경'],
  ['settings', 'delete', '계정 삭제'],
];
// 기기 틀·브라우저 창까지 포함한 화면 상자의 위치
function screenBox(p, index) {
  if (p === 'w') return { x: MOCK.originX + index * MOCK.webPitch, y: rowY('w') - 40, w: 1280 };
  const bezel = p === 'i' ? 14 : 10;
  return { x: MOCK.originX + index * MOCK.phonePitch - bezel, y: rowY(p) - bezel, w: (p === 'i' ? 402 : 412) + bezel * 2 };
}
// 화면 위쪽에서 위로 휘어 다음 화면 위쪽으로 내려오는 곡선. 건너뛰는 화면이 많을수록 높게 뜬다
async function flowArrow(page, p, from, to, label) {
  const a = FLOW_STEPS.findIndex((s) => s.key === from);
  const b = FLOW_STEPS.findIndex((s) => s.key === to);
  const sa = screenBox(p, a); const sb = screenBox(p, b);
  const start = { x: sa.x + sa.w * 0.6, y: sa.y - 8 };
  const end = { x: sb.x + sb.w * 0.4, y: sb.y - 8 };
  const lift = 60 + 40 * (Math.abs(b - a) - 1);
  const v = figma.createVector();
  v.name = `DS · Flow · ${PLATFORM_NAME[p]} · ${from} → ${to}`;
  const minX = Math.min(start.x, end.x); const minY = Math.min(start.y, end.y) - lift;
  await v.setVectorNetworkAsync({
    vertices: [
      { x: start.x - minX, y: start.y - minY, strokeCap: 'NONE' },
      { x: end.x - minX, y: end.y - minY, strokeCap: 'ARROW_LINES' },
    ],
    segments: [{ start: 0, end: 1, tangentStart: { x: 0, y: -lift * 1.3 }, tangentEnd: { x: 0, y: -lift * 1.3 } }],
  });
  v.x = minX; v.y = minY;
  v.fills = [];
  stroke(v, to === 'delete' ? 'destructive' : 'muted-foreground', null, 2);
  page.appendChild(v);
  const tag = row(0);
  tag.name = `DS · Flow · ${PLATFORM_NAME[p]} · ${label}`;
  pad(tag, 8, 3); tag.cornerRadius = TOKEN_RADIUS.sm; fill(tag, 'background'); stroke(tag, 'border');
  tag.appendChild(txt(label, 13, 'medium', to === 'delete' ? 'destructive' : 'foreground'));
  page.appendChild(tag);
  tag.x = (start.x + end.x) / 2 - tag.width / 2;
  tag.y = Math.min(start.y, end.y) - lift - tag.height / 2;
}

let TOKEN_RADIUS = {};

async function buildScreensPage() {
  const page = await getOrCreatePage('📱 Screens');
  TOKEN_RADIUS = Object.fromEntries(TOKENS.radius.map((r) => [r.key, r.value]));
  await loadMockFonts();
  for (const theme of ['light', 'dark']) {
    THEME = theme;
    SHIFT = theme === 'dark' ? DARK_SHIFT : 0;
    const before = new Set(page.children);
    // 구역 바탕: 캔버스 위에서도 라이트·다크가 구분되게 테마의 background 색을 깐다
    const backdrop = figma.createRectangle();
    backdrop.name = `DS · 바탕 · ${theme}`;
    backdrop.resize(MOCK.webPitch * FLOW_STEPS.length + 400, DARK_SHIFT - 200);
    backdrop.x = MOCK.originX - 320; backdrop.y = MOCK.labelY + SHIFT - 120;
    backdrop.fills = [tok('background')];
    page.appendChild(backdrop);
    mockLabels(page);
    for (const s of FLOW_STEPS) await step(`목업 · ${theme} · ${s.name}`, async () => MOCK_STEPS[s.key](page));
    for (const p of ['i', 'a', 'w']) {
      for (const [from, to, label] of FLOW_LINKS) await step(`흐름 · ${theme} · ${PLATFORM_NAME[p]} · ${from} → ${to}`, () => flowArrow(page, p, from, to, label));
    }
    if (theme === 'dark' && MODES.dark) {
      for (const node of page.children.filter((n) => !before.has(n))) node.setExplicitVariableModeForCollection(COL, MODES.dark);
    }
  }
  THEME = 'light';
  SHIFT = 0;
  figma.viewport.scrollAndZoomIntoView(page.children);
}

// ==========================================
// 7. Main Plugin Execution Entry
// ==========================================

async function main() {
  figma.notify(`KeyManage DS Builder 시작 · ${BUILD}`, { timeout: 2000 });
  // 페이지마다 loadAsync를 하지만, 다른 경로로 children을 읽는 경우까지 막기 위해 시작할 때 모든 페이지를 불러둔다
  await figma.loadAllPagesAsync();
  // 예전 버전이 만든 순서도 페이지는 더 쓰지 않는다
  const oldFlow = figma.root.children.find((pg) => pg.name === '🔀 User Flow');
  if (oldFlow && figma.root.children.length > 1) await step('예전 User Flow 페이지 삭제', async () => oldFlow.remove());
  await step('Variables 초기화', setupVariables);
  await step('Text Styles 초기화', setupTextStyles);
  await step('Page 1: Tokens 빌드', buildTokensPage);
  await step('Page 2: Components 빌드', buildComponentsPage);
  await step('Page 3: 플랫폼별 목업 빌드', buildScreensPage);

  if (LOG.length > 0) {
    figma.notify(`⚠️ [${BUILD}] 일부 경고가 있습니다: ${LOG.join('; ')}`, { timeout: 6000 });
  } else {
    figma.notify(`✅ [${BUILD}] KeyManage 디자인 시스템 및 화면 빌드 완료!`);
  }
  figma.closePlugin();
}

main();
