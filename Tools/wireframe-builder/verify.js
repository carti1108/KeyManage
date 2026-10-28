#!/usr/bin/env node
// Figma 없이 code.js를 실행해 보는 모의 검증기 (ADR-0009).
// 실제 Figma가 던지는 대표적인 오류 규칙과 동작을 흉내 낸다:
// - FILL 크기는 오토레이아웃 부모 안에서만, HUG는 오토레이아웃 프레임·텍스트에만
// - 텍스트는 불러온 글꼴로만 내용을 바꿀 수 있음
// - dynamic-page 모드에서는 currentPage 대입 금지, 다른 페이지 children은 loadAsync 뒤에만
// - 절대 위치(ABSOLUTE)는 오토레이아웃 부모 안에서만
// - 새로 만든 노드는 현재 페이지에 바로 붙는다 (그래서 남은 찌꺼기 노드를 잡을 수 있다)
// - constraints는 MIN·CENTER·MAX·STRETCH·SCALE만 받는다
// - 무료(Starter) 요금제는 페이지가 3개까지라 그 이상 createPage가 실패한다
// 시나리오: 새 파일에서 2회 실행(다시 그리기), 페이지가 꽉 찬 파일(현재 페이지가 비었을 때와 아닐 때), 글꼴 없음
// 사용법: node Tools/wireframe-builder/verify.js

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const CODE = fs.readFileSync(path.join(__dirname, 'code.js'), 'utf8');
const PAGE_NAME = '📐 Wireframes';
const EXPECTED_SCREENS = { iOS: 7, Android: 7, Web: 6, macOS: 6 };
const CONSTRAINT_VALUES = ['MIN', 'CENTER', 'MAX', 'STRETCH', 'SCALE'];
const STARTER_PAGE_LIMIT = 3;

// existingPages: [이름, 내용 노드 수] 목록. 첫 페이지가 현재 페이지다
function createWorld({ availableFonts, existingPages = [['Page 1', 0]] }) {
  const loadedFonts = new Set();
  const fontKey = (f) => `${f.family}/${f.style}`;
  const state = { notified: null, closed: false, errors: [] };

  const detach = (node) => {
    if (node.parent) node.parent._children = node.parent._children.filter((c) => c !== node);
    node.parent = null;
  };

  class Node {
    constructor(type) {
      this.type = type;
      this.parent = null;
      this._children = [];
      this.name = type;
      this.width = 100;
      this.height = 100;
      this.layoutMode = 'NONE';
      this._sizingH = 'FIXED';
      this._sizingV = 'FIXED';
      // 실제 Figma처럼 만들자마자 현재 페이지에 붙인다
      figma.currentPage.appendChild(this);
    }

    get children() { return this._children; }

    resize(w, h) {
      if (!(w >= 0.01 && h >= 0.01)) throw new Error(`${this.name}: resize 값이 잘못됨 (${w}, ${h})`);
      this.width = w;
      this.height = h;
    }

    appendChild(child) {
      if (['TEXT', 'RECTANGLE', 'ELLIPSE'].includes(this.type)) throw new Error(`${this.type}에는 자식을 넣을 수 없음`);
      detach(child);
      child.parent = this;
      this._children.push(child);
    }

    remove() { detach(this); this.removed = true; }

    isAutoLayout() { return this.layoutMode === 'HORIZONTAL' || this.layoutMode === 'VERTICAL'; }

    checkSizing(value, axis) {
      if (value === 'FILL' && !(this.parent && this.parent.isAutoLayout())) {
        throw new Error(`${this.name}: ${axis} FILL은 오토레이아웃 부모의 자식에만 설정할 수 있음`);
      }
      if (value === 'HUG' && !(this.type === 'TEXT' || this.isAutoLayout())) {
        throw new Error(`${this.name}: ${axis} HUG는 오토레이아웃 프레임이나 텍스트에만 설정할 수 있음`);
      }
    }

    set layoutSizingHorizontal(v) { this.checkSizing(v, '가로'); this._sizingH = v; }
    get layoutSizingHorizontal() { return this._sizingH; }
    set layoutSizingVertical(v) { this.checkSizing(v, '세로'); this._sizingV = v; }
    get layoutSizingVertical() { return this._sizingV; }

    set layoutPositioning(v) {
      if (v === 'ABSOLUTE' && !(this.parent && this.parent.isAutoLayout())) {
        throw new Error(`${this.name}: ABSOLUTE는 오토레이아웃 부모 안에서만 쓸 수 있음`);
      }
      this._positioning = v;
    }
    get layoutPositioning() { return this._positioning || 'AUTO'; }

    set constraints(v) {
      for (const axis of ['horizontal', 'vertical']) {
        if (!CONSTRAINT_VALUES.includes(v[axis])) throw new Error(`constraints.${axis} 값이 잘못됨: ${v[axis]}`);
      }
      this._constraints = v;
    }
    get constraints() { return this._constraints; }
  }

  class TextNode extends Node {
    constructor() {
      super('TEXT');
      this._font = { family: 'Inter', style: 'Regular' };
      this._chars = '';
    }

    set fontName(f) {
      if (!loadedFonts.has(fontKey(f))) throw new Error(`글꼴을 불러오지 않음: ${fontKey(f)}`);
      this._font = f;
    }
    get fontName() { return this._font; }

    set characters(v) {
      if (!loadedFonts.has(fontKey(this._font))) throw new Error(`글꼴을 불러오지 않고 텍스트를 바꿈: ${fontKey(this._font)}`);
      if (typeof v !== 'string') throw new Error('characters는 문자열이어야 함');
      this._chars = v;
    }
    get characters() { return this._chars; }
  }

  class PageNode {
    constructor(name) {
      this.type = 'PAGE';
      this.name = name;
      this._children = [];
      this.loaded = false;
    }

    get children() {
      if (!this.loaded && figma._current !== this) throw new Error(`${this.name}: loadAsync 전에는 children에 접근할 수 없음`);
      return this._children;
    }

    async loadAsync() { this.loaded = true; }

    isAutoLayout() { return false; }

    appendChild(child) {
      detach(child);
      child.parent = this;
      this._children.push(child);
    }
  }

  const pages = existingPages.map(([name]) => new PageNode(name));
  const figma = {
    _current: pages[0],
    root: { get children() { return pages; } },
    get currentPage() { return this._current; },
    set currentPage(_) { throw new Error('dynamic-page 모드에서는 currentPage에 대입할 수 없음: setCurrentPageAsync를 쓸 것'); },
    async setCurrentPageAsync(page) { await page.loadAsync(); this._current = page; },
    async loadFontAsync(font) {
      if (!availableFonts.includes(font.family)) throw new Error(`글꼴 없음: ${fontKey(font)}`);
      loadedFonts.add(fontKey(font));
    },
    createPage() {
      if (pages.length >= STARTER_PAGE_LIMIT) throw new Error('The Starter plan only comes with 3 pages.');
      const p = new PageNode('Page');
      pages.push(p);
      return p;
    },
    createFrame: () => new Node('FRAME'),
    createText: () => new TextNode(),
    createRectangle: () => new Node('RECTANGLE'),
    createEllipse: () => new Node('ELLIPSE'),
    viewport: { scrollAndZoomIntoView() {} },
    notify(message) { state.notified = message; },
    closePlugin() { state.closed = true; },
  };

  // 이미 있는 페이지에 사용자 내용을 넣어 둔다 (지워지면 안 되는 노드)
  existingPages.forEach(([, count], i) => {
    for (let n = 0; n < count; n += 1) {
      const node = new Node('FRAME');
      node.name = `사용자 내용 ${n}`;
      pages[i].appendChild(node);
    }
  });
  return { pages, state, figma };
}

async function run(world) {
  world.state.closed = false;
  world.state.notified = null;
  // 플러그인을 다시 실행하는 것처럼 스크립트 실행 환경은 매번 새로 만들고, 문서(figma)는 그대로 둔다
  const sandbox = { figma: world.figma, console: { error: (m) => world.state.errors.push(String(m)), log() {} } };
  vm.createContext(sandbox);
  vm.runInContext(CODE, sandbox, { filename: 'code.js' });
  const started = Date.now();
  while (!world.state.closed && Date.now() - started < 10000) await new Promise((r) => setTimeout(r, 10));
  if (!world.state.closed) world.state.errors.push('closePlugin이 호출되지 않음');
}

function checkWireframes(world, label) {
  const problems = [];
  const page = world.pages.find((p) => p.name === PAGE_NAME);
  if (!page) return [`${label}: ${PAGE_NAME} 페이지가 없음`];
  const roots = page._children;
  if (roots.length !== 1) problems.push(`${label}: 최상위 노드가 ${roots.length}개 (전체 보드 1개여야 함, 남은 노드가 있음)`);
  const root = roots[0];
  for (const [platform, expected] of Object.entries(EXPECTED_SCREENS)) {
    const board = root && root.children.find((b) => b.name === `${platform} Wireframes`);
    const screens = board ? board.children.filter((c) => c.name.startsWith('Column/')).length : 0;
    if (screens !== expected) problems.push(`${label}: ${platform} 화면 ${screens}개 (기대 ${expected}개)`);
  }
  if (!String(world.state.notified).startsWith('✅')) problems.push(`${label}: 완료 알림이 아님: ${world.state.notified}`);
  return problems;
}

const userContent = (world) => world.pages.map((p) => p._children.filter((n) => n.name.startsWith('사용자 내용')).length);

(async () => {
  const problems = [];

  // 1) 새 파일: 두 번 실행해 "같은 페이지를 지우고 다시 그리기"까지 확인한다
  const fresh = createWorld({ availableFonts: ['Inter', 'Noto Sans KR'] });
  for (const label of ['새 파일 1회차', '새 파일 2회차']) {
    await run(fresh);
    problems.push(...checkWireframes(fresh, label));
  }
  if (fresh.pages.length !== 2) problems.push(`새 파일: 페이지가 ${fresh.pages.length}개 (기본 1 + 와이어프레임 1이어야 함)`);
  if (fresh.pages[0]._children.length > 0) problems.push(`새 파일: 기본 페이지에 남은 노드 ${fresh.pages[0]._children.length}개`);
  problems.push(...fresh.state.errors.map((e) => `새 파일: ${e}`));

  // 2) 페이지가 꽉 찬 파일, 현재 페이지가 비어 있음: 그 페이지에 그리고 다른 페이지는 건드리지 않는다
  const fullEmpty = createWorld({ availableFonts: ['Inter', 'Noto Sans KR'], existingPages: [['Page 1', 0], ['디자인', 2], ['메모', 1]] });
  await run(fullEmpty);
  await run(fullEmpty);
  problems.push(...checkWireframes(fullEmpty, '꽉 찬 파일(빈 현재 페이지)'));
  if (JSON.stringify(userContent(fullEmpty)) !== JSON.stringify([0, 2, 1])) problems.push(`꽉 찬 파일: 사용자 내용이 바뀜 ${JSON.stringify(userContent(fullEmpty))}`);

  // 3) 페이지가 꽉 찬 파일, 현재 페이지에 내용이 있음: 아무것도 지우지 않고 오류 알림 후 닫는다
  const fullBusy = createWorld({ availableFonts: ['Inter', 'Noto Sans KR'], existingPages: [['Page 1', 3], ['디자인', 2], ['메모', 1]] });
  await run(fullBusy);
  if (!String(fullBusy.state.notified).startsWith('❌')) problems.push(`꽉 찬 파일(내용 있음): 오류 알림이 아님: ${fullBusy.state.notified}`);
  if (JSON.stringify(userContent(fullBusy)) !== JSON.stringify([3, 2, 1])) problems.push(`꽉 찬 파일(내용 있음): 사용자 내용이 바뀜 ${JSON.stringify(userContent(fullBusy))}`);
  if (fullBusy.pages[0]._children.length !== 3) problems.push(`꽉 찬 파일(내용 있음): 현재 페이지에 노드가 남음 ${fullBusy.pages[0]._children.length - 3}개`);

  // 4) 글꼴이 하나도 없으면 페이지를 만들지 않고 오류 알림 후 닫는다
  const noFonts = createWorld({ availableFonts: [] });
  await run(noFonts);
  if (noFonts.pages.length !== 1) problems.push(`글꼴 없음: 페이지가 ${noFonts.pages.length - 1}개 만들어짐 (0개여야 함)`);
  if (!String(noFonts.state.notified).startsWith('❌')) problems.push(`글꼴 없음: 오류 알림이 아님: ${noFonts.state.notified}`);

  if (problems.length > 0) {
    console.log('실패:');
    problems.forEach((p) => console.log(` - ${p}`));
    process.exit(1);
  }
  const counts = Object.entries(EXPECTED_SCREENS).map(([name, n]) => `${name} ${n}`).join(', ');
  console.log(`새 파일 2회 실행: ${PAGE_NAME} 한 페이지에 ${counts} 화면, 남은 노드 없음`);
  console.log('페이지가 꽉 찬 파일: 빈 현재 페이지에만 그리고, 내용 있는 페이지는 건드리지 않음');
  console.log('글꼴 없음: 페이지를 만들지 않고 중단');
  console.log('검증 통과');
})();
