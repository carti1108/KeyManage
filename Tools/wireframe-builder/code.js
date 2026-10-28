// KeyManage Wireframe Builder — Figma 개발 플러그인
// 플랫폼별(iOS · Android · Web · macOS) 저충실도 와이어프레임을 그린다.
// 색과 글꼴은 일부러 회색조로 단순화했다: 지금은 "무엇이 어디에 놓이는가"를 정하는 단계다.
// 화면 구성은 docs/policies(SEC-001·002, VLT-001, GEN-001, COM-001)와 docs/api-spec.md를 따른다.

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
// 1. 회색조 팔레트와 플랫폼 규격
// ==========================================

const C = {
  screen: '#FFFFFF',
  canvas: '#F3F4F6',
  panel: '#F9FAFB',
  line: '#D1D5DB',
  fill: '#E5E7EB',
  shape: '#9CA3AF',
  text: '#111827',
  sub: '#6B7280',
  accent: '#374151',
  accentText: '#FFFFFF',
  selected: '#E0E7FF',
  note: '#FEF3C7',
  noteText: '#78350F',
  danger: '#B91C1C',
};

// fs: 본문 글자 크기, btn·field: 버튼·입력 높이 (각 플랫폼 가이드의 기본 크기에 맞춤)
const PLATFORMS = {
  ios: { key: 'ios', label: 'iOS', w: 393, h: 852, fs: 17, btn: 50, field: 48, radius: 12, mobile: true },
  android: { key: 'android', label: 'Android', w: 412, h: 915, fs: 16, btn: 48, field: 56, radius: 24, mobile: true },
  web: { key: 'web', label: 'Web', w: 1440, h: 900, fs: 14, btn: 40, field: 40, radius: 8, mobile: false },
  macos: { key: 'macos', label: 'macOS', w: 1280, h: 800, fs: 13, btn: 28, field: 28, radius: 6, mobile: false },
};

const ITEMS = [
  ['GitHub', 'octocat@github.com'],
  ['Google', 'me@gmail.com'],
  ['Netflix', 'family@example.com'],
  ['토스', '010-****-1234'],
  ['Slack', 'me@company.com'],
  ['AWS 콘솔', 'admin'],
];

let FONT = { regular: { family: 'Inter', style: 'Regular' }, bold: { family: 'Inter', style: 'Bold' } };

// 한글 글리프가 있는 글꼴을 먼저 쓰고, 없으면 Inter로 대체한다
async function loadFonts() {
  const candidates = [['Noto Sans KR', 'Regular', 'Bold'], ['Inter', 'Regular', 'Bold']];
  for (const [family, regular, bold] of candidates) {
    try {
      await figma.loadFontAsync({ family, style: regular });
      await figma.loadFontAsync({ family, style: bold });
      FONT = { regular: { family, style: regular }, bold: { family, style: bold } };
      return;
    } catch (error) {
      LOG.push(`${family} 글꼴을 불러오지 못해 다음 글꼴로 대체함`);
    }
  }
  throw new Error('사용할 수 있는 글꼴이 없음');
}

// ==========================================
// 2. 기본 도형 도우미 (오토레이아웃)
// ==========================================

function rgb(hex) {
  const v = hex.slice(1);
  const n = (i) => parseInt(v.slice(i, i + 2), 16) / 255;
  return { r: n(0), g: n(2), b: n(4) };
}

function solid(hex) {
  return [{ type: 'SOLID', color: rgb(hex) }];
}

// o: dir, pad(숫자 또는 [위,오른쪽,아래,왼쪽]), gap, fill, stroke, radius, main, cross, w, h
function box(name, o = {}) {
  const f = figma.createFrame();
  f.name = name;
  f.layoutMode = o.dir || 'VERTICAL';
  const p = o.pad === undefined ? 0 : o.pad;
  const [top, right, bottom, left] = Array.isArray(p) ? p : [p, p, p, p];
  f.paddingTop = top;
  f.paddingRight = right;
  f.paddingBottom = bottom;
  f.paddingLeft = left;
  f.itemSpacing = o.gap || 0;
  f.cornerRadius = o.radius || 0;
  f.fills = o.fill ? solid(o.fill) : [];
  if (o.stroke) {
    f.strokes = solid(o.stroke);
    f.strokeWeight = 1;
  }
  f.primaryAxisAlignItems = o.main || 'MIN';
  f.counterAxisAlignItems = o.cross || 'MIN';
  f.resize(o.w || 100, o.h || 100);
  f.layoutSizingHorizontal = o.w ? 'FIXED' : 'HUG';
  f.layoutSizingVertical = o.h ? 'FIXED' : 'HUG';
  return f;
}

function text(characters, o = {}) {
  const t = figma.createText();
  t.fontName = o.bold ? FONT.bold : FONT.regular;
  t.characters = characters;
  t.fontSize = o.size || 14;
  t.fills = solid(o.color || C.text);
  if (o.align) t.textAlignHorizontal = o.align;
  return t;
}

// 오토레이아웃 부모에 넣은 뒤에만 FILL을 설정할 수 있다
function place(parent, child, o = {}) {
  parent.appendChild(child);
  if (o.fillW) child.layoutSizingHorizontal = 'FILL';
  if (o.fillH) child.layoutSizingVertical = 'FILL';
  if (child.type === 'TEXT' && o.fillW) child.textAutoResize = 'HEIGHT';
  return child;
}

function circle(size, hex) {
  const e = figma.createEllipse();
  e.resize(size, size);
  e.fills = solid(hex);
  return e;
}

// 아이콘 자리: 실제 아이콘은 디자인 단계에서 정한다
function icon(size = 20, hex = C.shape) {
  const r = figma.createRectangle();
  r.name = 'icon';
  r.resize(size, size);
  r.cornerRadius = 4;
  r.fills = solid(hex);
  return r;
}

function spacer(parent) {
  const s = box('spacer');
  place(parent, s, { fillW: true, fillH: true });
  return s;
}

// ==========================================
// 3. 공용 UI 조각
// ==========================================

function button(P, label, kind = 'primary') {
  const styles = {
    primary: { fill: C.accent, color: C.accentText },
    secondary: { fill: C.screen, stroke: C.line, color: C.text },
    ghost: { color: C.text },
    danger: { fill: C.screen, stroke: C.danger, color: C.danger },
  }[kind];
  const b = box(`Button/${label}`, {
    dir: 'HORIZONTAL', pad: [0, 16, 0, 16], h: P.btn, radius: P.key === 'android' ? P.btn / 2 : P.radius,
    fill: styles.fill, stroke: styles.stroke, main: 'CENTER', cross: 'CENTER',
  });
  place(b, text(label, { size: P.fs, bold: kind === 'primary', color: styles.color }));
  return b;
}

// 라벨과 입력 칸. trailing: 입력 칸 오른쪽 작은 버튼 이름들(예: 보기, 복사)
function field(P, label, value, o = {}) {
  const wrap = box(`Field/${label}`, { gap: 6 });
  place(wrap, text(label, { size: P.fs - 3, color: C.sub }), { fillW: true });
  const input = box('input', {
    dir: 'HORIZONTAL', pad: [0, 12, 0, 12], gap: 8, h: o.tall ? P.field * 2 : P.field,
    radius: P.key === 'android' ? 4 : P.radius, stroke: C.line, fill: C.screen,
    cross: o.tall ? 'MIN' : 'CENTER',
  });
  if (o.tall) input.paddingTop = 10;
  place(input, text(value, { size: P.fs, color: o.placeholder ? C.shape : C.text }), { fillW: true });
  for (const name of o.trailing || []) {
    const tag = box(`Action/${name}`, { pad: [4, 8, 4, 8], radius: 4, fill: C.fill });
    place(tag, text(name, { size: P.fs - 3 }));
    place(input, tag);
  }
  place(wrap, input, { fillW: true });
  return wrap;
}

function searchBar(P, placeholder = '검색') {
  const s = box('SearchBar', {
    dir: 'HORIZONTAL', pad: [0, 12, 0, 12], gap: 8, h: P.mobile ? 40 : P.field,
    radius: P.mobile ? 10 : P.radius, fill: C.fill, cross: 'CENTER',
  });
  place(s, icon(16));
  place(s, text(placeholder, { size: P.fs, color: C.sub }));
  return s;
}

function listRow(P, title, sub, o = {}) {
  const row = box(`Row/${title}`, {
    dir: 'HORIZONTAL', pad: [10, 12, 10, 12], gap: 12, cross: 'CENTER',
    radius: P.mobile ? 0 : P.radius, fill: o.selected ? C.selected : null,
  });
  place(row, circle(P.mobile ? 40 : 32, C.fill));
  const texts = box('texts', { gap: 2 });
  place(texts, text(title, { size: P.fs, bold: true }), { fillW: true });
  place(texts, text(sub, { size: P.fs - 2, color: C.sub }), { fillW: true });
  place(row, texts, { fillW: true });
  if (P.mobile) place(row, text('›', { size: P.fs + 4, color: C.shape }));
  return row;
}

function banner(P, message) {
  const b = box('Banner/오프라인', { dir: 'HORIZONTAL', pad: [10, 12, 10, 12], gap: 8, radius: 8, fill: C.fill, cross: 'CENTER' });
  place(b, icon(16, C.sub));
  place(b, text(message, { size: P.fs - 3, color: C.text }), { fillW: true });
  return b;
}

// 값의 비율만큼 채운 막대. 막대 길이는 부모를 따라 늘어나므로 채운 부분은 SCALE 제약으로 비율을 유지한다
function ratioBar(ratio, height) {
  const track = figma.createFrame();
  track.name = 'track';
  track.layoutMode = 'NONE';
  track.resize(100, height);
  track.cornerRadius = height / 2;
  track.fills = solid(C.fill);
  const filled = figma.createRectangle();
  filled.name = 'value';
  filled.resize(Math.max(1, Math.round(100 * ratio)), height);
  filled.cornerRadius = height / 2;
  filled.fills = solid(C.accent);
  track.appendChild(filled);
  filled.constraints = { horizontal: 'SCALE', vertical: 'TOP_BOTTOM' };
  return track;
}

// GEN-001 점수 게이지: 60 미만 취약, 60~79 보통, 80 이상 안전
function gauge(P, score) {
  const level = score >= 80 ? '안전' : score >= 60 ? '보통' : '취약';
  const g = box('KMGaugeBar', { gap: 6 });
  const head = box('head', { dir: 'HORIZONTAL' });
  place(head, text('안전 점수', { size: P.fs - 3, color: C.sub }), { fillW: true });
  place(head, text(`${score}점 · ${level}`, { size: P.fs - 3, bold: true }));
  place(g, head, { fillW: true });
  place(g, ratioBar(score / 100, 8), { fillW: true });
  return g;
}

function toggleRow(P, label, on) {
  const row = box(`Toggle/${label}`, { dir: 'HORIZONTAL', pad: [8, 0, 8, 0], cross: 'CENTER' });
  place(row, text(label, { size: P.fs }), { fillW: true });
  const sw = box('switch', {
    dir: 'HORIZONTAL', w: 44, h: 26, radius: 13, pad: 3, fill: on ? C.accent : C.line,
    main: on ? 'MAX' : 'MIN', cross: 'CENTER',
  });
  place(sw, circle(20, C.screen));
  place(row, sw);
  return row;
}

function slider(P, label, value, min, max) {
  const s = box(`Slider/${label}`, { gap: 8 });
  const head = box('head', { dir: 'HORIZONTAL' });
  place(head, text(label, { size: P.fs }), { fillW: true });
  place(head, text(`${value}자 (${min}~${max})`, { size: P.fs, bold: true }));
  place(s, head, { fillW: true });
  place(s, ratioBar((value - min) / (max - min), 6), { fillW: true });
  return s;
}

// COM-001: 복사 후 30초 뒤 클립보드를 비운다. 문구는 정책 원문을 쓰고 남은 초를 함께 보여 준다
function burnBadge(P, seconds = 29) {
  const b = box('KMBurnBadge', { dir: 'HORIZONTAL', pad: [8, 12, 8, 12], gap: 8, radius: 12, fill: C.fill, cross: 'CENTER' });
  const count = box('count', { w: 28, h: 28, radius: 14, fill: C.accent, main: 'CENTER', cross: 'CENTER' });
  place(count, text(String(seconds), { size: 12, bold: true, color: C.accentText }));
  place(b, count);
  place(b, text('보안을 위해 복사된 비밀번호가 30초 후 클립보드에서 자동 삭제됩니다.', { size: P.fs - 3 }), { fillW: true });
  return b;
}

function sectionTitle(P, title) {
  return text(title, { size: P.fs - 3, bold: true, color: C.sub });
}

function passwordBox(P, value) {
  const b = box('GeneratedPassword', { pad: 16, radius: P.radius, fill: C.canvas, main: 'CENTER', cross: 'CENTER' });
  place(b, text(value, { size: P.fs + 5, bold: true, align: 'CENTER' }), { fillW: true });
  return b;
}

function row(name, o = {}) {
  return box(name, { dir: 'HORIZONTAL', gap: o.gap === undefined ? 8 : o.gap, cross: 'CENTER' });
}

// ==========================================
// 4. 화면 내용 (플랫폼 공통)
// ==========================================

function loginContent(c, P) {
  place(c, text('KeyManage', { size: P.fs + 15, bold: true }), { fillW: true });
  place(c, text('마스터 비밀번호는 기기 밖으로 나가지 않습니다.', { size: P.fs - 2, color: C.sub }), { fillW: true });
  place(c, field(P, '이메일', 'you@example.com', { placeholder: true }), { fillW: true });
  place(c, field(P, '마스터 비밀번호', '••••••••••••', { trailing: ['보기'] }), { fillW: true });
  place(c, button(P, '로그인'), { fillW: true });
  place(c, button(P, '새 계정 만들기', 'ghost'), { fillW: true });
  place(c, text('서버: keymanage.example.com (변경)', { size: P.fs - 4, color: C.sub, align: 'CENTER' }), { fillW: true });
}

function registerContent(c, P) {
  place(c, text('계정 만들기', { size: P.fs + 11, bold: true }), { fillW: true });
  place(c, field(P, '이메일', 'you@example.com', { placeholder: true }), { fillW: true });
  place(c, field(P, '마스터 비밀번호', '••••••••••••••••', { trailing: ['보기'] }), { fillW: true });
  place(c, gauge(P, 84), { fillW: true });
  place(c, field(P, '마스터 비밀번호 확인', '••••••••••••••••'), { fillW: true });
  // SEC-001 사용자 문구 원문
  place(c, banner(P, '마스터 키를 분실하면 복구할 수 없습니다.'), { fillW: true });
  place(c, button(P, '계정 만들기'), { fillW: true });
}

function vaultListContent(c, P, o = {}) {
  if (o.offline) place(c, banner(P, '오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.'), { fillW: true });
  place(c, searchBar(P), { fillW: true });
  const list = box('List', { gap: P.mobile ? 0 : 4 });
  ITEMS.forEach(([title, sub], i) => place(list, listRow(P, title, sub, { selected: o.selectedIndex === i }), { fillW: true }));
  place(c, list, { fillW: true });
}

function detailContent(c, P) {
  const head = row('Header', { gap: 12 });
  place(head, circle(P.mobile ? 56 : 48, C.fill));
  const titles = box('titles', { gap: 2 });
  place(titles, text('GitHub', { size: P.fs + 7, bold: true }), { fillW: true });
  place(titles, text('github.com/login', { size: P.fs - 2, color: C.sub }), { fillW: true });
  place(head, titles, { fillW: true });
  place(c, head, { fillW: true });
  place(c, field(P, '아이디', 'octocat@github.com', { trailing: ['복사'] }), { fillW: true });
  place(c, field(P, '비밀번호', '••••••••••••••••', { trailing: ['보기', '복사'] }), { fillW: true });
  place(c, burnBadge(P), { fillW: true });
  place(c, gauge(P, 100), { fillW: true });
  place(c, field(P, '메모', '개인 개발용 서브 계정', { tall: true }), { fillW: true });
  place(c, text('수정 2026-10-27 · 생성 2026-10-20', { size: P.fs - 4, color: C.sub }), { fillW: true });
  const actions = row('Actions');
  place(actions, button(P, '수정', 'secondary'), { fillW: true });
  place(actions, button(P, '삭제', 'danger'), { fillW: true });
  place(c, actions, { fillW: true });
}

function editContent(c, P) {
  place(c, field(P, '서비스 이름', 'GitHub'), { fillW: true });
  place(c, field(P, '아이디', 'octocat@github.com'), { fillW: true });
  place(c, field(P, '비밀번호', 'p9$K#m2@L9vX!q4R', { trailing: ['생성'] }), { fillW: true });
  place(c, gauge(P, 100), { fillW: true });
  place(c, field(P, 'URL', 'https://github.com/login'), { fillW: true });
  place(c, field(P, '메모', '개인 개발용 서브 계정', { tall: true }), { fillW: true });
}

function generatorContent(c, P) {
  place(c, passwordBox(P, 'p9$K#m2@L9vX!q4R'), { fillW: true });
  place(c, gauge(P, 100), { fillW: true });
  place(c, slider(P, '길이', 16, 8, 64), { fillW: true });
  const toggles = box('문자 종류', { gap: 0 });
  for (const [label, on] of [['대문자 (A-Z)', true], ['소문자 (a-z)', true], ['숫자 (0-9)', true], ['특수기호 (!@#…)', true]]) {
    place(toggles, toggleRow(P, label, on), { fillW: true });
  }
  place(c, toggles, { fillW: true });
  const actions = row('Actions');
  place(actions, button(P, '다시 생성', 'secondary'), { fillW: true });
  place(actions, button(P, '복사'), { fillW: true });
  place(c, actions, { fillW: true });
}

function settingsContent(c, P) {
  const group = (title, rows) => {
    place(c, sectionTitle(P, title), { fillW: true });
    const g = box(title, { radius: P.radius, fill: P.mobile ? C.panel : null, stroke: P.mobile ? null : C.line });
    rows.forEach(([label, value]) => {
      const r = box(label, { dir: 'HORIZONTAL', pad: [12, 16, 12, 16], cross: 'CENTER' });
      place(r, text(label, { size: P.fs }), { fillW: true });
      place(r, text(value, { size: P.fs, color: C.sub }));
      place(g, r, { fillW: true });
    });
    place(c, g, { fillW: true });
  };
  group('계정', [['이메일', 'you@example.com'], ['마지막 동기화', '방금 전']]);
  // 정책에 있는 값만 보여 준다. 클립보드 30초는 COM-001의 고정값이라 바꿀 수 없다
  group('보안', [['클립보드 자동 삭제', '30초 (고정)']]);
  group('서버', [['서버 주소', 'keymanage.example.com']]);
  place(c, button(P, '로그아웃', 'danger'), { fillW: true });
}

// ==========================================
// 5. 플랫폼 화면 틀
// ==========================================

function device(P, name, dir = 'VERTICAL', fill = C.screen) {
  const d = box(name, { dir, w: P.w, h: P.h, fill, stroke: C.line, radius: P.mobile ? 44 : 10 });
  d.clipsContent = true;
  return d;
}

function statusBar(P) {
  const s = box('StatusBar', { dir: 'HORIZONTAL', pad: [0, 28, 0, 28], h: P.key === 'ios' ? 54 : 32, cross: 'CENTER' });
  place(s, text('9:41', { size: 15, bold: true }), { fillW: true });
  place(s, icon(18));
  place(s, icon(18));
  return s;
}

// iOS 큰 제목 내비게이션
function iosLargeTitle(P, title, trailing = []) {
  const h = box('NavBar/Large', { pad: [4, 16, 8, 16], gap: 4 });
  const actions = box('trailing', { dir: 'HORIZONTAL', gap: 16, h: 24, main: 'MAX', cross: 'CENTER' });
  trailing.forEach((t) => place(actions, text(t, { size: P.fs + 5, color: C.accent, bold: true })));
  place(h, actions, { fillW: true });
  place(h, text(title, { size: 34, bold: true }), { fillW: true });
  return h;
}

// iOS 기본 내비게이션 바: 왼쪽 · 가운데 제목 · 오른쪽
function iosNavBar(P, left, title, right) {
  const n = box('NavBar', { dir: 'HORIZONTAL', pad: [0, 16, 0, 16], h: 44, cross: 'CENTER' });
  const l = box('left', { dir: 'HORIZONTAL' });
  place(l, text(left, { size: P.fs, color: C.accent }));
  place(n, l, { fillW: true });
  place(n, text(title, { size: P.fs, bold: true }));
  const r = box('right', { dir: 'HORIZONTAL', main: 'MAX' });
  if (right) place(r, text(right, { size: P.fs, color: C.accent, bold: true }));
  place(n, r, { fillW: true });
  return n;
}

function iosTabBar(P, active) {
  const t = box('TabBar', { dir: 'HORIZONTAL', pad: [8, 0, 30, 0], fill: C.panel, stroke: C.line });
  ['볼트', '생성기', '설정'].forEach((label, i) => {
    const item = box(`Tab/${label}`, { gap: 4, cross: 'CENTER' });
    const color = i === active ? C.text : C.shape;
    place(item, icon(24, color));
    place(item, text(label, { size: 10, bold: i === active, color }));
    place(t, item, { fillW: true });
  });
  return t;
}

function androidTopBar(P, title, o = {}) {
  const b = box('TopAppBar', { dir: 'HORIZONTAL', pad: [0, 16, 0, 16], gap: 16, h: 64, cross: 'CENTER' });
  if (o.back) place(b, text('←', { size: 22 }));
  place(b, text(title, { size: 22, bold: !o.back }), { fillW: true });
  (o.actions || []).forEach((a) => place(b, text(a, { size: P.fs, bold: true })));
  return b;
}

function androidNavBar(P, active) {
  const n = box('NavigationBar', { dir: 'HORIZONTAL', pad: [12, 0, 16, 0], fill: C.panel });
  ['볼트', '생성기', '설정'].forEach((label, i) => {
    const item = box(`Nav/${label}`, { gap: 4, cross: 'CENTER' });
    const pill = box('indicator', { w: 64, h: 32, radius: 16, fill: i === active ? C.selected : null, main: 'CENTER', cross: 'CENTER' });
    place(pill, icon(22, i === active ? C.text : C.shape));
    place(item, pill);
    place(item, text(label, { size: 12, bold: i === active }));
    place(n, item, { fillW: true });
  });
  return n;
}

function androidFab() {
  const f = box('FAB', { w: 56, h: 56, radius: 16, fill: C.accent, main: 'CENTER', cross: 'CENTER' });
  place(f, text('+', { size: 26, color: C.accentText }));
  return f;
}

// 모바일 화면: 상태 표시줄 · 머리 · 내용 · 하단 막대 (· FAB, fabBottom은 화면 아래에서 FAB까지 거리)
function mobileScreen(P, name, { header, body, bottom, fab, fabBottom = 0, pad = [8, 16, 16, 16] }) {
  const s = device(P, name);
  place(s, statusBar(P), { fillW: true });
  if (header) place(s, header, { fillW: true });
  const content = box('Content', { pad, gap: 14 });
  place(s, content, { fillW: true, fillH: true });
  body(content);
  if (bottom) place(s, bottom, { fillW: true });
  if (fab) {
    s.appendChild(fab);
    fab.layoutPositioning = 'ABSOLUTE';
    fab.x = P.w - 16 - 56;
    fab.y = P.h - fabBottom - 56;
  }
  return s;
}

// 데스크톱 사이드바 메뉴
function sidebar(P, active, width, fill) {
  const s = box('Sidebar', { w: width, pad: [16, 12, 16, 12], gap: 4, fill, stroke: C.line });
  if (P.key === 'web') {
    place(s, text('KeyManage', { size: 18, bold: true }), { fillW: true });
    place(s, box('gap', { h: 16 }));
  }
  ['볼트', '생성기', '설정'].forEach((label, i) => {
    const item = box(`Menu/${label}`, { dir: 'HORIZONTAL', pad: [8, 10, 8, 10], gap: 10, radius: P.radius, fill: i === active ? C.selected : null, cross: 'CENTER' });
    place(item, icon(16, i === active ? C.text : C.shape));
    place(item, text(label, { size: P.fs, bold: i === active }));
    place(s, item, { fillW: true });
  });
  spacer(s);
  place(s, text('you@example.com', { size: P.fs - 2, color: C.sub }), { fillW: true });
  place(s, text('로그아웃', { size: P.fs - 2, color: C.danger }), { fillW: true });
  return s;
}

function macTitleBar(P, title, o = {}) {
  const b = box('TitleBar', { dir: 'HORIZONTAL', pad: [0, 16, 0, 16], gap: 8, h: 52, fill: C.panel, stroke: C.line, cross: 'CENTER' });
  ['#F87171', '#FBBF24', '#34D399'].forEach((hex) => place(b, circle(12, hex)));
  place(b, box('gap', { w: 12, h: 1 }));
  place(b, text(title, { size: P.fs, bold: true }), { fillW: true });
  if (o.toolbar) {
    const s = searchBar(P);
    s.layoutSizingHorizontal = 'FIXED';
    s.resize(200, P.field);
    place(b, s);
    place(b, button(P, '+', 'secondary'));
  }
  return b;
}

// 데스크톱 3분할: 사이드바 · 목록 · 상세. center만 주면 사이드바 + 가운데 영역
function desktopScreen(P, name, { active, list, detail, center }) {
  const s = device(P, name, 'VERTICAL', C.screen);
  let main = s;
  if (P.key === 'macos') {
    place(s, macTitleBar(P, 'KeyManage', { toolbar: !!list }), { fillW: true });
    main = box('Body', { dir: 'HORIZONTAL' });
    place(s, main, { fillW: true, fillH: true });
  } else {
    s.layoutMode = 'HORIZONTAL';
  }
  place(main, sidebar(P, active, P.key === 'web' ? 240 : 200, P.key === 'web' ? C.panel : C.canvas), { fillH: true });
  if (center) {
    const area = box('Main', { pad: 40, gap: 16, fill: C.screen, cross: 'CENTER' });
    place(main, area, { fillW: true, fillH: true });
    const card = box('Card', { w: 520, gap: 16 });
    place(area, card);
    center(card);
    return s;
  }
  const listCol = box('ListColumn', { w: P.key === 'web' ? 360 : 300, pad: 16, gap: 12, stroke: C.line });
  place(main, listCol, { fillH: true });
  list(listCol);
  const detailCol = box('Detail', { pad: [32, 40, 32, 40], gap: 16 });
  place(main, detailCol, { fillW: true, fillH: true });
  const inner = box('DetailBody', { w: 560, gap: 16 });
  place(detailCol, inner);
  detail(inner);
  return s;
}

// 로그인·가입처럼 메뉴가 없는 데스크톱 화면
function desktopAuthScreen(P, name, body) {
  const s = device(P, name, 'VERTICAL', C.canvas);
  let area = s;
  if (P.key === 'macos') {
    place(s, macTitleBar(P, 'KeyManage'), { fillW: true });
    area = box('Body', { main: 'CENTER', cross: 'CENTER' });
    place(s, area, { fillW: true, fillH: true });
  } else {
    s.primaryAxisAlignItems = 'CENTER';
    s.counterAxisAlignItems = 'CENTER';
  }
  const card = box('Card', { w: 400, pad: 32, gap: 16, radius: 12, fill: C.screen, stroke: C.line });
  place(area, card);
  body(card);
  return s;
}

function listPane(P) {
  return (col) => {
    const head = row('ListHeader');
    place(head, text('볼트', { size: P.fs + 6, bold: true }), { fillW: true });
    if (P.key === 'web') place(head, button(P, '+ 새 항목', 'primary'));
    place(col, head, { fillW: true });
    vaultListContent(col, P, { selectedIndex: 0 });
  };
}

// ==========================================
// 6. 플랫폼별 화면 목록과 설명 메모
// ==========================================

const NOTES = {
  login: '로그인 전 prelogin으로 계정 salt를 받아 기기에서 키를 만든다. 서버로는 인증 해시만 보낸다 (SEC-002, api-spec 2.5).',
  register: '마스터 비밀번호 강도를 GEN-001 점수로 보여 준다. 잊으면 복구 불가 경고 (SEC-001).',
  list: '오프라인이면 상단 배너, 다시 연결되면 자동 동기화. 충돌(409)은 자동 병합, 3회 실패 시에만 "동기화 충돌" 문구 (VLT-001).',
  detail: '복사하면 30초 뒤 클립보드를 비우고 배지로 남은 시간을 보여 준다 (COM-001).',
  edit: '저장하면 기기에서 볼트 전체를 다시 암호화해 revision과 함께 올린다 (VLT-001, ADR-0006).',
  generator: '길이 8~64(기본 16), 문자 종류 최소 1개. 거부 샘플링으로 생성하고 점수를 바로 보여 준다 (GEN-001).',
  settings: '서버 주소는 셀프 호스팅·시연 서버 전환용. 로그아웃 시 이 기기 토큰만 폐기 (SEC-002).',
};

const SCREENS = {
  ios: (P) => [
    ['01 로그인', NOTES.login, () => mobileScreen(P, 'iOS/로그인', { body: (c) => { spacer(c); loginContent(c, P); spacer(c); } })],
    ['02 가입', NOTES.register, () => mobileScreen(P, 'iOS/가입', { header: iosNavBar(P, '‹ 로그인', '', null), body: (c) => registerContent(c, P) })],
    ['03 볼트 목록 (오프라인)', NOTES.list, () => mobileScreen(P, 'iOS/볼트', { header: iosLargeTitle(P, '볼트', ['+']), body: (c) => vaultListContent(c, P, { offline: true }), bottom: iosTabBar(P, 0) })],
    ['04 항목 상세 (복사 직후)', NOTES.detail, () => mobileScreen(P, 'iOS/상세', { header: iosNavBar(P, '‹ 볼트', '', '편집'), body: (c) => detailContent(c, P), bottom: iosTabBar(P, 0) })],
    ['05 항목 편집 (시트)', NOTES.edit, () => mobileScreen(P, 'iOS/편집', { header: iosNavBar(P, '취소', '항목 편집', '저장'), body: (c) => editContent(c, P) })],
    ['06 생성기', NOTES.generator, () => mobileScreen(P, 'iOS/생성기', { header: iosLargeTitle(P, '생성기'), body: (c) => generatorContent(c, P), bottom: iosTabBar(P, 1) })],
    ['07 설정', NOTES.settings, () => mobileScreen(P, 'iOS/설정', { header: iosLargeTitle(P, '설정'), body: (c) => settingsContent(c, P), bottom: iosTabBar(P, 2) })],
  ],
  android: (P) => [
    ['01 로그인', NOTES.login, () => mobileScreen(P, 'Android/로그인', { body: (c) => { spacer(c); loginContent(c, P); spacer(c); } })],
    ['02 가입', NOTES.register, () => mobileScreen(P, 'Android/가입', { header: androidTopBar(P, '계정 만들기', { back: true }), body: (c) => registerContent(c, P) })],
    ['03 볼트 목록 (오프라인)', NOTES.list, () => mobileScreen(P, 'Android/볼트', { header: androidTopBar(P, '볼트'), body: (c) => vaultListContent(c, P, { offline: true }), bottom: androidNavBar(P, 0), fab: androidFab(), fabBottom: 108 })],
    ['04 항목 상세 (복사 직후)', NOTES.detail, () => mobileScreen(P, 'Android/상세', { header: androidTopBar(P, 'GitHub', { back: true, actions: ['수정'] }), body: (c) => detailContent(c, P), bottom: androidNavBar(P, 0) })],
    ['05 항목 편집', NOTES.edit, () => mobileScreen(P, 'Android/편집', { header: androidTopBar(P, '항목 편집', { back: true, actions: ['저장'] }), body: (c) => editContent(c, P) })],
    ['06 생성기', NOTES.generator, () => mobileScreen(P, 'Android/생성기', { header: androidTopBar(P, '생성기'), body: (c) => generatorContent(c, P), bottom: androidNavBar(P, 1) })],
    ['07 설정', NOTES.settings, () => mobileScreen(P, 'Android/설정', { header: androidTopBar(P, '설정'), body: (c) => settingsContent(c, P), bottom: androidNavBar(P, 2) })],
  ],
  web: (P) => desktopScreens(P),
  macos: (P) => desktopScreens(P),
};

// Web과 macOS는 같은 3분할 구성을 쓰고, 틀(사이드바 폭, 창 제목 막대 등)만 desktopScreen이 플랫폼에 맞춘다
function desktopScreens(P) {
  const heading = (parent, title) => place(parent, text(title, { size: P.fs + 7, bold: true }), { fillW: true });
  const editPanel = (d) => {
    heading(d, '항목 편집');
    editContent(d, P);
    const actions = row('Actions');
    place(actions, button(P, '취소', 'secondary'));
    place(actions, button(P, '저장'));
    place(d, actions);
  };
  return [
    ['01 로그인', NOTES.login, () => desktopAuthScreen(P, `${P.label}/로그인`, (c) => loginContent(c, P))],
    ['02 가입', NOTES.register, () => desktopAuthScreen(P, `${P.label}/가입`, (c) => registerContent(c, P))],
    ['03 볼트 (목록 + 상세, 복사 직후)', `${NOTES.list} ${NOTES.detail}`, () => desktopScreen(P, `${P.label}/볼트`, { active: 0, list: listPane(P), detail: (d) => detailContent(d, P) })],
    ['04 항목 편집 (상세 패널)', NOTES.edit, () => desktopScreen(P, `${P.label}/편집`, { active: 0, list: listPane(P), detail: editPanel })],
    ['05 생성기', NOTES.generator, () => desktopScreen(P, `${P.label}/생성기`, { active: 1, center: (card) => { heading(card, '비밀번호 생성기'); generatorContent(card, P); } })],
    ['06 설정', NOTES.settings, () => desktopScreen(P, `${P.label}/설정`, { active: 2, center: (card) => { heading(card, '설정'); settingsContent(card, P); } })],
  ];
}

const OVERVIEW = {
  ios: 'iOS: 하단 탭 3개(볼트·생성기·설정), NavigationStack 큰 제목, 편집은 시트로 띄운다. 클립보드 만료와 Keychain 토큰 보관은 OS 기능을 쓴다.',
  android: 'Android: Material 3. 상단 앱 바, 하단 내비게이션 3개, 볼트 목록의 + 는 FAB. 뒤로 가기는 시스템 제스처와 ← 버튼.',
  web: 'Web: 사이드바 · 목록 · 상세 3분할. 편집은 상세 패널 안에서 전환한다. 정적 사이트로 배포해 서버가 화면을 만들지 않는다.',
  macos: 'macOS: 창 하나에 사이드바 · 목록 · 상세(NavigationSplitView). 검색과 + 는 툴바에 둔다. iOS와 SwiftUI 코드를 공유한다.',
};

// ==========================================
// 7. 페이지 구성
// ==========================================

function note(message, width) {
  const n = box('Note', { w: width, pad: 12, radius: 8, fill: C.note });
  place(n, text(message, { size: 13, color: C.noteText }), { fillW: true });
  return n;
}

async function getOrCreatePage(name) {
  let page = figma.root.children.find((p) => p.name === name);
  if (!page) {
    page = figma.createPage();
    page.name = name;
  }
  await page.loadAsync();
  for (const child of [...page.children]) child.remove();
  return page;
}

async function buildPlatformPage(P) {
  const page = await getOrCreatePage(`📐 ${P.label}`);
  await figma.setCurrentPageAsync(page);

  const board = box(`${P.label} Wireframes`, { dir: 'HORIZONTAL', pad: 80, gap: 80, fill: C.canvas });
  page.appendChild(board);

  const intro = box('Intro', { w: 360, gap: 12 });
  place(intro, text(`${P.label} 와이어프레임`, { size: 32, bold: true }), { fillW: true });
  place(intro, text(`${P.w} × ${P.h}`, { size: 14, color: C.sub }), { fillW: true });
  place(intro, note(OVERVIEW[P.key], 360));
  place(intro, note('회색조 저충실도 시안입니다. 색·글꼴·아이콘은 디자인 시스템(DS Builder) 단계에서 입힙니다.', 360));
  place(board, intro);

  for (const [caption, memo, build] of SCREENS[P.key](P)) {
    // 새 노드는 만들자마자 현재 페이지에 붙으므로, 화면 하나가 중간에 실패하면 그사이 생긴 노드를 지운다
    const before = new Set(page.children);
    const built = await step(`${P.label} ${caption}`, async () => {
      const column = box(`Column/${caption}`, { gap: 16 });
      place(board, column);
      place(column, text(caption, { size: 20, bold: true }));
      place(column, build());
      place(column, note(memo, Math.min(P.w, 420)));
      return column;
    });
    if (!built) {
      for (const child of [...page.children]) if (!before.has(child)) child.remove();
      for (const child of [...board.children]) if (child.name === `Column/${caption}`) child.remove();
    }
  }
  figma.viewport.scrollAndZoomIntoView([board]);
}

async function main() {
  // 글꼴이 없으면 모든 텍스트가 실패하므로 페이지를 만들지 않고 멈춘다
  const fontsLoaded = await step('글꼴 불러오기', async () => { await loadFonts(); return true; });
  if (!fontsLoaded) {
    figma.notify(`❌ 글꼴을 불러오지 못해 중단했습니다: ${LOG.join('; ')}`, { error: true, timeout: 8000 });
    figma.closePlugin();
    return;
  }
  for (const P of Object.values(PLATFORMS)) {
    await step(`${P.label} 페이지`, () => buildPlatformPage(P));
  }
  if (LOG.length > 0) {
    figma.notify(`⚠️ 일부 경고: ${LOG.join('; ')}`, { timeout: 8000 });
  } else {
    figma.notify('✅ 플랫폼별 와이어프레임 생성 완료 (iOS · Android · Web · macOS)');
  }
  figma.closePlugin();
}

main();
