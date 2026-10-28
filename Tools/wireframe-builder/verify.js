#!/usr/bin/env node
// Figma 없이 code.js를 실행해 보는 모의 검증기 (ADR-0009).
// 실제 Figma가 던지는 대표적인 오류 규칙과 동작을 흉내 낸다:
// - FILL 크기는 오토레이아웃 부모 안에서만, HUG는 오토레이아웃 프레임·텍스트에만
// - 텍스트는 불러온 글꼴로만 내용을 바꿀 수 있음
// - dynamic-page 모드에서는 currentPage 대입 금지, 다른 페이지 children은 loadAsync 뒤에만
// - 절대 위치(ABSOLUTE)는 오토레이아웃 부모 안에서만
// - 새로 만든 노드는 현재 페이지에 바로 붙는다 (그래서 남은 찌꺼기 노드를 잡을 수 있다)
// 시나리오: 정상 실행 2회(다시 그리기 확인), 글꼴이 하나도 없을 때 중단 확인
// 사용법: node Tools/wireframe-builder/verify.js

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const CODE = fs.readFileSync(path.join(__dirname, 'code.js'), 'utf8');
const EXPECTED_SCREENS = { '📐 iOS': 7, '📐 Android': 7, '📐 Web': 6, '📐 macOS': 6 };

function createWorld({ availableFonts }) {
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

  const pages = [new PageNode('Page 1')];
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
    createPage() { const p = new PageNode('Page'); pages.push(p); return p; },
    createFrame: () => new Node('FRAME'),
    createText: () => new TextNode(),
    createRectangle: () => new Node('RECTANGLE'),
    createEllipse: () => new Node('ELLIPSE'),
    viewport: { scrollAndZoomIntoView() {} },
    notify(message) { state.notified = message; },
    closePlugin() { state.closed = true; },
  };

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

function checkPages(world, label) {
  const problems = [];
  for (const [name, expected] of Object.entries(EXPECTED_SCREENS)) {
    const page = world.pages.find((p) => p.name === name);
    if (!page) { problems.push(`${label}: ${name} 페이지가 없음`); continue; }
    const roots = page._children;
    if (roots.length !== 1) problems.push(`${label}: ${name}의 최상위 노드가 ${roots.length}개 (보드 1개여야 함, 남은 노드가 있음)`);
    const board = roots.find((n) => n.name.endsWith('Wireframes'));
    const screens = board ? board.children.filter((c) => c.name.startsWith('Column/')).length : 0;
    if (screens !== expected) problems.push(`${label}: ${name} 화면 ${screens}개 (기대 ${expected}개)`);
  }
  const stray = world.pages[0]._children.length;
  if (stray > 0) problems.push(`${label}: 기본 페이지에 남은 노드 ${stray}개`);
  return problems;
}

(async () => {
  const problems = [];

  // 1) 정상 실행을 두 번 해 "같은 페이지를 지우고 다시 그리기"까지 확인한다
  const normal = createWorld({ availableFonts: ['Inter', 'Noto Sans KR'] });
  for (const label of ['1회차', '2회차']) {
    await run(normal);
    problems.push(...checkPages(normal, label));
    if (!String(normal.state.notified).startsWith('✅')) problems.push(`${label}: 완료 알림이 아님: ${normal.state.notified}`);
  }
  problems.push(...normal.state.errors);
  const counts = Object.keys(EXPECTED_SCREENS).map((name) => `${name} ${EXPECTED_SCREENS[name]}`).join(', ');

  // 2) 글꼴이 하나도 없으면 페이지를 만들지 않고 오류 알림 후 닫혀야 한다
  const noFonts = createWorld({ availableFonts: [] });
  await run(noFonts);
  if (noFonts.pages.length !== 1) problems.push(`글꼴 없음: 페이지가 ${noFonts.pages.length - 1}개 만들어짐 (0개여야 함)`);
  if (!String(noFonts.state.notified).startsWith('❌')) problems.push(`글꼴 없음: 오류 알림이 아님: ${noFonts.state.notified}`);

  if (problems.length > 0) {
    console.log('실패:');
    problems.forEach((p) => console.log(` - ${p}`));
    process.exit(1);
  }
  console.log(`정상 실행 2회: ${counts} 화면, 남은 노드 없음`);
  console.log('글꼴 없음: 페이지를 만들지 않고 중단');
  console.log('검증 통과');
})();
