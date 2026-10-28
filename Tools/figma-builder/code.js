// KeyManage DS Builder — Figma Development Plugin
// Builds the KeyManage design system, tokens, components, and screens in Figma.
// Compatible with fresh empty Figma files (auto-creates variable collections).

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
// 1. Tokens & Color Definitions (shadcn/ui)
// ==========================================

const PRIMITIVE_COLORS = [
  ['slate/900', '#0F172A'],
  ['slate/800', '#1E293B'],
  ['slate/700', '#334155'],
  ['slate/500', '#64748B'],
  ['slate/400', '#94A3B8'],
  ['slate/200', '#E2E8F0'],
  ['slate/50', '#F8FAFC'],
  ['sky/400', '#38BDF8'],
  ['emerald/500', '#10B981'],
  ['emerald/600', '#059669'],
  ['red/500', '#EF4444'],
  ['red/600', '#DC2626'],
  ['white', '#FFFFFF'],
  ['black', '#000000'],
  ['border/subtle', '#FFFFFF14'], // rgba(255,255,255,0.08)
];

const SEMANTIC_COLORS = [
  ['color/background', 'slate/900'],
  ['color/foreground', 'slate/50'],
  ['color/card', 'slate/800'],
  ['color/card-foreground', 'slate/50'],
  ['color/primary', 'sky/400'],
  ['color/primary-foreground', 'slate/900'],
  ['color/accent', 'emerald/500'],
  ['color/accent-foreground', 'slate/900'],
  ['color/destructive', 'red/500'],
  ['color/destructive-foreground', 'white'],
  ['color/muted', 'slate/800'],
  ['color/muted-foreground', 'slate/400'],
  ['color/border', 'border/subtle'],
  ['color/input', 'slate/700'],
  ['color/ring', 'sky/400'],
];

const TYPOGRAPHY_STYLES = {
  'heading/large': { family: 'Inter', style: 'Bold', size: 32, lineHeight: 40 },
  'heading/title': { family: 'Inter', style: 'Semi Bold', size: 20, lineHeight: 28 },
  'body/medium': { family: 'Inter', style: 'Medium', size: 16, lineHeight: 24 },
  'body/regular': { family: 'Inter', style: 'Regular', size: 16, lineHeight: 24 },
  'password/mono': { family: 'Roboto Mono', style: 'Medium', size: 16, lineHeight: 24 },
  'caption/small': { family: 'Inter', style: 'Regular', size: 12, lineHeight: 16 },
};

let COL = {};
let ALL_VARS = [];
let TS = {};

// Helper: Hex to Figma RGBA
function hexToRgba(hex) {
  const v = hex.slice(1);
  const n = (i) => parseInt(v.slice(i, i + 2), 16) / 255;
  return { r: n(0), g: n(2), b: n(4), a: v.length === 8 ? parseInt(v.slice(6, 8), 16) / 255 : 1 };
}

function findVar(collection, name) {
  return ALL_VARS.find((v) => v.variableCollectionId === COL[collection].id && v.name === name);
}

function ensureVariable(collection, name, type, value, scopes = []) {
  let v = findVar(collection, name);
  if (!v) {
    v = figma.variables.createVariable(name, COL[collection], type);
    v.setValueForMode(COL[collection].modes[0].modeId, value);
    ALL_VARS.push(v);
  }
  v.scopes = scopes;
  return v;
}

const V = (name) => {
  const v = findVar('semantic', name);
  if (!v) throw new Error(`semantic 변수 없음: ${name}`);
  return v;
};

// ==========================================
// 2. Setup Variables and Text Styles
// ==========================================

async function setupVariables() {
  const collections = await figma.variables.getLocalVariableCollectionsAsync();
  let prim = collections.find((c) => c.name === 'primitive');
  let sem = collections.find((c) => c.name === 'semantic');

  if (!prim) prim = figma.variables.createVariableCollection('primitive');
  if (!sem) sem = figma.variables.createVariableCollection('semantic');

  COL = { primitive: prim, semantic: sem };
  ALL_VARS = await figma.variables.getLocalVariablesAsync();

  // Primitive 컬러 등록
  for (const [name, hex] of PRIMITIVE_COLORS) {
    ensureVariable('primitive', name, 'COLOR', hexToRgba(hex));
  }

  // Semantic 컬러 등록 (Primitive Alias)
  for (const [name, target] of SEMANTIC_COLORS) {
    const targetVar = findVar('primitive', target);
    if (targetVar) {
      const alias = { type: 'VARIABLE_ALIAS', id: targetVar.id };
      ensureVariable('semantic', name, 'COLOR', alias);
    }
  }
}

async function setupTextStyles() {
  await figma.loadFontAsync({ family: 'Inter', style: 'Regular' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Medium' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Semi Bold' });
  await figma.loadFontAsync({ family: 'Inter', style: 'Bold' });
  await figma.loadFontAsync({ family: 'Roboto Mono', style: 'Medium' });

  const localStyles = await figma.getLocalTextStylesAsync();
  for (const [name, spec] of Object.entries(TYPOGRAPHY_STYLES)) {
    let ts = localStyles.find((s) => s.name === name);
    if (!ts) ts = figma.createTextStyle();
    ts.name = name;
    ts.fontName = { family: spec.family, style: spec.style };
    ts.fontSize = spec.size;
    ts.lineHeight = { unit: 'PIXELS', value: spec.lineHeight };
    TS[name] = ts.id;
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

function getOrCreatePage(name) {
  let page = figma.root.children.find((p) => p.name === name);
  if (!page) {
    page = figma.createPage();
    page.name = name;
  }
  // Clear existing page children
  for (const child of page.children) child.remove();
  return page;
}

// ==========================================
// 4. Page 1: Tokens Specimen
// ==========================================

async function buildTokensPage() {
  const page = getOrCreatePage('🎨 Tokens');
  figma.currentPage = page;

  const container = createFrame('Tokens Specimen Container', 'VERTICAL', 40, 40, 0, 'color/background');
  container.x = 0;
  container.y = 0;

  const header = await createText('KeyManage Design Tokens (shadcn/ui)', 'heading/large', 'color/foreground');
  container.appendChild(header);

  // 1. Colors Section
  const colorSection = createFrame('Color Tokens Section', 'VERTICAL', 24, 16, 16, 'color/card');
  const colorTitle = await createText('Semantic Color Tokens', 'heading/title', 'color/primary');
  colorSection.appendChild(colorTitle);

  const grid = figma.createFrame();
  grid.name = 'Colors Grid';
  grid.layoutMode = 'HORIZONTAL';
  grid.layoutWrap = 'WRAP';
  grid.itemSpacing = 16;
  grid.counterAxisSpacing = 16;
  grid.fills = [];
  grid.resize(800, 300);

  for (const [name, target] of SEMANTIC_COLORS) {
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

    const label = await createText(name.replace('color/', ''), 'body/medium', 'color/foreground');
    const hex = PRIMITIVE_COLORS.find((p) => p[0] === target);
    const subLabel = await createText(hex ? hex[1] : target, 'caption/small', 'color/muted-foreground');
    chip.appendChild(label);
    chip.appendChild(subLabel);
    grid.appendChild(chip);
  }

  colorSection.appendChild(grid);
  container.appendChild(colorSection);

  // 2. Typography Section
  const typeSection = createFrame('Typography Section', 'VERTICAL', 24, 20, 16, 'color/card');
  const typeTitle = await createText('Typography Scale', 'heading/title', 'color/accent');
  typeSection.appendChild(typeTitle);

  for (const [name, spec] of Object.entries(TYPOGRAPHY_STYLES)) {
    const row = figma.createFrame();
    row.layoutMode = 'HORIZONTAL';
    row.itemSpacing = 24;
    row.fills = [];
    row.primaryAxisAlignItems = 'CENTER';

    const sample = await createText(`Aa · ${name}`, name, 'color/foreground');
    const info = await createText(`${spec.family} ${spec.style} ${spec.size}px`, 'caption/small', 'color/muted-foreground');
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
  const page = getOrCreatePage('🧩 Components');

  const container = createFrame('Components Specimen Container', 'VERTICAL', 40, 40, 0, 'color/background');
  container.x = 0;
  container.y = 0;

  const header = await createText('KeyManage Core 5 UI Components', 'heading/large', 'color/foreground');
  container.appendChild(header);

  // 1. Buttons (KMButton)
  const btnSection = createFrame('1. KMButton Variants', 'VERTICAL', 24, 16, 16, 'color/card');
  btnSection.appendChild(await createText('Buttons (Primary / Outline / Destructive / Ghost)', 'heading/title', 'color/foreground'));

  const btnRow = figma.createFrame();
  btnRow.layoutMode = 'HORIZONTAL';
  btnRow.itemSpacing = 16;
  btnRow.fills = [];

  // Primary
  const b1 = createFrame('KMButton [Primary]', 'HORIZONTAL', 16, 8, 8, 'color/primary');
  b1.paddingTop = 10; b1.paddingBottom = 10;
  b1.appendChild(await createText('비밀번호 생성', 'body/medium', 'color/primary-foreground'));
  btnRow.appendChild(b1);

  // Outline
  const b2 = createFrame('KMButton [Outline]', 'HORIZONTAL', 16, 8, 8, null);
  b2.paddingTop = 10; b2.paddingBottom = 10;
  b2.strokes = [paint('color/border')];
  b2.strokeWeight = 1;
  b2.appendChild(await createText('클립보드 복사', 'body/medium', 'color/foreground'));
  btnRow.appendChild(b2);

  // Destructive
  const b3 = createFrame('KMButton [Destructive]', 'HORIZONTAL', 16, 8, 8, 'color/destructive');
  b3.paddingTop = 10; b3.paddingBottom = 10;
  b3.appendChild(await createText('볼트 삭제', 'body/medium', 'color/destructive-foreground'));
  btnRow.appendChild(b3);

  // Accent (Secure Action)
  const b4 = createFrame('KMButton [Accent Secure]', 'HORIZONTAL', 16, 8, 8, 'color/accent');
  b4.paddingTop = 10; b4.paddingBottom = 10;
  b4.appendChild(await createText('볼트 잠금 해제', 'body/medium', 'color/accent-foreground'));
  btnRow.appendChild(b4);

  btnSection.appendChild(btnRow);
  container.appendChild(btnSection);

  // 2. KMPasswordField
  const passSection = createFrame('2. KMPasswordField', 'VERTICAL', 24, 16, 16, 'color/card');
  passSection.appendChild(await createText('Password Input Field (Masked & Monospace)', 'heading/title', 'color/foreground'));

  const field = createFrame('KMPasswordField [Container]', 'HORIZONTAL', 16, 12, 10, 'color/input');
  field.strokes = [paint('color/border')];
  field.strokeWeight = 1;
  field.resize(400, 52);
  field.primaryAxisAlignItems = 'SPACE_BETWEEN';
  field.counterAxisAlignItems = 'CENTER';

  const maskedText = await createText('••••••••••••••••', 'password/mono', 'color/foreground');
  const actionText = await createText('보기 / 복사', 'caption/small', 'color/primary');
  field.appendChild(maskedText);
  field.appendChild(actionText);

  passSection.appendChild(field);
  container.appendChild(passSection);

  // 3. KMGaugeBar (Entropy Score)
  const gaugeSection = createFrame('3. KMGaugeBar', 'VERTICAL', 24, 16, 16, 'color/card');
  gaugeSection.appendChild(await createText('Entropy Strength Gauge Bar (3-Tier Status)', 'heading/title', 'color/foreground'));

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
  g1.appendChild(await createText('초강력 비밀번호 · 96점', 'caption/small', 'color/accent'));
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
  g2.appendChild(await createText('취약한 비밀번호 · 38점', 'caption/small', 'color/destructive'));
  g2.appendChild(g2Bar);
  gaugeSection.appendChild(g2);

  container.appendChild(gaugeSection);

  // 4. KMBurnBadge
  const badgeSection = createFrame('4. KMBurnBadge', 'VERTICAL', 24, 16, 16, 'color/card');
  badgeSection.appendChild(await createText('Auto-Burn Timer Badge (30 Seconds)', 'heading/title', 'color/foreground'));

  const badge = createFrame('KMBurnBadge [Badge]', 'HORIZONTAL', 12, 6, 14, 'color/destructive');
  badge.paddingTop = 6; badge.paddingBottom = 6;
  badge.appendChild(await createText('⏱️ 30초 후 클립보드 자동 소각', 'caption/small', 'color/destructive-foreground'));
  badgeSection.appendChild(badge);

  container.appendChild(badgeSection);

  // 5. KMCard (Vault Account Item)
  const cardSection = createFrame('5. KMCard (Vault Item)', 'VERTICAL', 24, 16, 16, 'color/card');
  cardSection.appendChild(await createText('Vault Credential Card', 'heading/title', 'color/foreground'));

  const vCard = createFrame('KMCard [Item]', 'VERTICAL', 16, 10, 12, 'color/muted');
  vCard.resize(400, 90);
  vCard.strokes = [paint('color/border')];
  vCard.strokeWeight = 1;

  const topRow = figma.createFrame();
  topRow.layoutMode = 'HORIZONTAL';
  topRow.fills = [];
  topRow.primaryAxisAlignItems = 'SPACE_BETWEEN';
  topRow.resize(368, 24);
  topRow.appendChild(await createText('GitHub', 'heading/title', 'color/foreground'));
  topRow.appendChild(await createText('96점 (안전)', 'caption/small', 'color/accent'));

  vCard.appendChild(topRow);
  vCard.appendChild(await createText('octocat@github.com', 'body/regular', 'color/muted-foreground'));

  cardSection.appendChild(vCard);
  container.appendChild(cardSection);
}

// ==========================================
// 6. Page 3: App Screens (iPhone 16 Pro)
// ==========================================

async function buildScreensPage() {
  const page = getOrCreatePage('📱 Screens');

  const wrapper = figma.createFrame();
  wrapper.name = 'Screens Wrapper';
  wrapper.layoutMode = 'HORIZONTAL';
  wrapper.itemSpacing = 48;
  wrapper.fills = [solidPaint('#0B0F19')];
  wrapper.paddingLeft = 60;
  wrapper.paddingRight = 60;
  wrapper.paddingTop = 60;
  wrapper.paddingBottom = 60;
  wrapper.x = 0;
  wrapper.y = 0;

  // ------------------------------------------
  // Screen 1: Vault Dashboard (393 x 852)
  // ------------------------------------------
  const s1 = figma.createFrame();
  s1.name = 'Screen: Vault Dashboard';
  s1.resize(393, 852);
  s1.fills = [paint('color/background')];
  s1.layoutMode = 'VERTICAL';
  s1.paddingLeft = 20;
  s1.paddingRight = 20;
  s1.paddingTop = 56;
  s1.paddingBottom = 40;
  s1.itemSpacing = 20;
  s1.cornerRadius = 44;
  s1.clipsContent = true;

  // App Bar
  const bar = figma.createFrame();
  bar.layoutMode = 'HORIZONTAL';
  bar.fills = [];
  bar.resize(353, 36);
  bar.primaryAxisAlignItems = 'SPACE_BETWEEN';
  bar.counterAxisAlignItems = 'CENTER';
  bar.appendChild(await createText('KeyManage', 'heading/large', 'color/foreground'));
  bar.appendChild(await createText('🔒 잠금', 'caption/small', 'color/primary'));
  s1.appendChild(bar);

  // Security Audit Card
  const auditCard = createFrame('Audit Summary Card', 'VERTICAL', 16, 12, 16, 'color/card');
  auditCard.resize(353, 110);
  auditCard.strokes = [paint('color/border')];
  auditCard.strokeWeight = 1;
  auditCard.appendChild(await createText('전체 보안 안전도: 96점', 'heading/title', 'color/accent'));
  auditCard.appendChild(await createText('등록된 계정 30개 중 28개 안전 (초강력)', 'caption/small', 'color/muted-foreground'));

  const miniBar = figma.createFrame();
  miniBar.resize(321, 6);
  miniBar.cornerRadius = 3;
  miniBar.fills = [paint('color/input')];
  const miniFill = figma.createFrame();
  miniFill.resize(308, 6);
  miniFill.cornerRadius = 3;
  miniFill.fills = [paint('color/accent')];
  miniBar.appendChild(miniFill);
  auditCard.appendChild(miniBar);
  s1.appendChild(auditCard);

  // Generator Action Button
  const genBtn = createFrame('Generate CTA Button', 'HORIZONTAL', 16, 8, 12, 'color/primary');
  genBtn.resize(353, 50);
  genBtn.primaryAxisAlignItems = 'CENTER';
  genBtn.counterAxisAlignItems = 'CENTER';
  genBtn.appendChild(await createText('⚡ 새 강력한 비밀번호 생성', 'body/medium', 'color/primary-foreground'));
  s1.appendChild(genBtn);

  // Vault Items List Header
  s1.appendChild(await createText('보관된 계정 (3개)', 'heading/title', 'color/foreground'));

  // Account Items
  const accounts = [
    { title: 'Google', user: 'user@gmail.com', score: '98점' },
    { title: 'GitHub', user: 'octocat@github.com', score: '96점' },
    { title: 'Apple ID', user: 'dev@icloud.com', score: '92점' },
  ];

  for (const acc of accounts) {
    const card = createFrame(`Item: ${acc.title}`, 'VERTICAL', 14, 6, 12, 'color/card');
    card.resize(353, 76);
    card.strokes = [paint('color/border')];
    card.strokeWeight = 1;

    const r1 = figma.createFrame();
    r1.layoutMode = 'HORIZONTAL';
    r1.fills = [];
    r1.resize(325, 22);
    r1.primaryAxisAlignItems = 'SPACE_BETWEEN';
    r1.appendChild(await createText(acc.title, 'body/medium', 'color/foreground'));
    r1.appendChild(await createText(acc.score, 'caption/small', 'color/accent'));

    card.appendChild(r1);
    card.appendChild(await createText(acc.user, 'caption/small', 'color/muted-foreground'));
    s1.appendChild(card);
  }

  wrapper.appendChild(s1);

  // ------------------------------------------
  // Screen 2: Password Generator Modal (393 x 852)
  // ------------------------------------------
  const s2 = figma.createFrame();
  s2.name = 'Screen: Password Generator';
  s2.resize(393, 852);
  s2.fills = [paint('color/background')];
  s2.layoutMode = 'VERTICAL';
  s2.paddingLeft = 20;
  s2.paddingRight = 20;
  s2.paddingTop = 56;
  s2.paddingBottom = 40;
  s2.itemSpacing = 20;
  s2.cornerRadius = 44;
  s2.clipsContent = true;

  // Title
  s2.appendChild(await createText('스마트 비밀번호 생성기', 'heading/large', 'color/foreground'));

  // Generated Password Display Card
  const genCard = createFrame('Generated Password Card', 'VERTICAL', 20, 14, 16, 'color/card');
  genCard.resize(353, 140);
  genCard.strokes = [paint('color/border')];
  genCard.strokeWeight = 1;

  genCard.appendChild(await createText('생성된 암호 (16자리)', 'caption/small', 'color/muted-foreground'));
  genCard.appendChild(await createText('p9$K#m2@L9vX!q4R', 'password/mono', 'color/accent'));

  const copyRow = figma.createFrame();
  copyRow.layoutMode = 'HORIZONTAL';
  copyRow.fills = [];
  copyRow.resize(313, 36);
  copyRow.primaryAxisAlignItems = 'SPACE_BETWEEN';
  copyRow.counterAxisAlignItems = 'CENTER';

  const copyBtn = createFrame('Copy Btn', 'HORIZONTAL', 12, 6, 6, 'color/input');
  copyBtn.appendChild(await createText('📋 클립보드 복사', 'caption/small', 'color/foreground'));
  copyRow.appendChild(copyBtn);
  copyRow.appendChild(await createText('엔트로피: 96점', 'caption/small', 'color/accent'));
  genCard.appendChild(copyRow);
  s2.appendChild(genCard);

  // Burn Alert Badge
  const burnBadge = createFrame('Burn Badge', 'HORIZONTAL', 12, 8, 8, 'color/destructive');
  burnBadge.resize(353, 34);
  burnBadge.primaryAxisAlignItems = 'CENTER';
  burnBadge.appendChild(await createText('⏱️ 복사 30초 후 메모리 자동 소각', 'caption/small', 'color/destructive-foreground'));
  s2.appendChild(burnBadge);

  // Options Box
  const optCard = createFrame('Options Box', 'VERTICAL', 16, 12, 16, 'color/card');
  optCard.resize(353, 160);
  optCard.appendChild(await createText('길이 조절: 16자', 'body/medium', 'color/foreground'));
  optCard.appendChild(await createText('• 대문자 포함 (A-Z): ON', 'caption/small', 'color/muted-foreground'));
  optCard.appendChild(await createText('• 소문자 포함 (a-z): ON', 'caption/small', 'color/muted-foreground'));
  optCard.appendChild(await createText('• 숫자 포함 (0-9): ON', 'caption/small', 'color/muted-foreground'));
  optCard.appendChild(await createText('• 특수문자 (!@#$): ON', 'caption/small', 'color/muted-foreground'));
  s2.appendChild(optCard);

  // Save to Vault CTA
  const saveBtn = createFrame('Save CTA Button', 'HORIZONTAL', 16, 8, 12, 'color/accent');
  saveBtn.resize(353, 50);
  saveBtn.primaryAxisAlignItems = 'CENTER';
  saveBtn.counterAxisAlignItems = 'CENTER';
  saveBtn.appendChild(await createText('볼트에 안전하게 저장', 'body/medium', 'color/accent-foreground'));
  s2.appendChild(saveBtn);

  wrapper.appendChild(s2);
}

// ==========================================
// 7. Main Plugin Execution Entry
// ==========================================

async function main() {
  await step('Variables 초기화', setupVariables);
  await step('Text Styles 초기화', setupTextStyles);
  await step('Page 1: Tokens 빌드', buildTokensPage);
  await step('Page 2: Components 빌드', buildComponentsPage);
  await step('Page 3: Screens 빌드', buildScreensPage);

  if (LOG.length > 0) {
    figma.notify(`⚠️ 일부 경고가 있습니다: ${LOG.join('; ')}`, { timeout: 6000 });
  } else {
    figma.notify('✅ KeyManage 디자인 시스템 및 화면 빌드 완료!');
  }
  figma.closePlugin();
}

main();
