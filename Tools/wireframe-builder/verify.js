#!/usr/bin/env node
// Figma 없이 code.js를 실행해 보는 모의 검증기 (ADR-0009).
// 실제 Figma가 던지는 대표적인 오류 규칙을 흉내 낸다:
// - FILL 크기는 오토레이아웃 부모 안에서만, HUG는 오토레이아웃 프레임·텍스트에만
// - 텍스트는 불러온 글꼴로만 내용을 바꿀 수 있음
// - dynamic-page 모드에서는 currentPage 대입 금지, 다른 페이지 children은 loadAsync 뒤에만
// - 절대 위치(ABSOLUTE)는 오토레이아웃 부모 안에서만
// 사용법: node Tools/wireframe-builder/verify.js

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const loadedFonts = new Set();
const fontKey = (f) => `${f.family}/${f.style}`;
const counts = { FRAME: 0, TEXT: 0, RECTANGLE: 0, ELLIPSE: 0 };
let notified = null;
let closed = false;

class Node {
  constructor(type) {
    this.type = type;
    this.parent = null;
    this.children = [];
    this.name = type;
    this.width = 100;
    this.height = 100;
    this.layoutMode = 'NONE';
    this._sizingH = 'FIXED';
    this._sizingV = 'FIXED';
    this.pluginData = {};
    counts[type] = (counts[type] || 0) + 1;
  }

  resize(w, h) {
    if (!(w >= 0.01 && h >= 0.01)) throw new Error(`${this.name}: resize 값이 잘못됨 (${w}, ${h})`);
    this.width = w;
    this.height = h;
  }

  appendChild(child) {
    if (this.type === 'TEXT' || this.type === 'RECTANGLE' || this.type === 'ELLIPSE') {
      throw new Error(`${this.type}에는 자식을 넣을 수 없음`);
    }
    if (child.parent) child.parent.children = child.parent.children.filter((c) => c !== child);
    child.parent = this;
    this.children.push(child);
  }

  remove() {
    if (this.parent) this.parent.children = this.parent.children.filter((c) => c !== this);
    this.parent = null;
  }

  isAutoLayout() {
    return this.layoutMode === 'HORIZONTAL' || this.layoutMode === 'VERTICAL';
  }

  checkSizing(value, axis) {
    if (value === 'FILL' && !(this.parent && this.parent.isAutoLayout && this.parent.isAutoLayout())) {
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

  setPluginData(key, value) { this.pluginData[key] = value; }
  getPluginData(key) { return this.pluginData[key] || ''; }
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

  appendChild(child) {
    if (child.parent) child.parent.children = child.parent.children.filter((c) => c !== child);
    child.parent = { children: this._children, isAutoLayout: () => false };
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
    // 기본 Figma 환경처럼 Inter와 Noto Sans KR이 있다고 가정한다
    if (!['Inter', 'Noto Sans KR'].includes(font.family)) throw new Error(`글꼴 없음: ${fontKey(font)}`);
    loadedFonts.add(fontKey(font));
  },
  createPage() { const p = new PageNode('Page'); pages.push(p); return p; },
  createFrame: () => new Node('FRAME'),
  createText: () => new TextNode(),
  createRectangle: () => new Node('RECTANGLE'),
  createEllipse: () => new Node('ELLIPSE'),
  viewport: { scrollAndZoomIntoView() {} },
  notify(message) { notified = message; },
  closePlugin() { closed = true; },
};

const code = fs.readFileSync(path.join(__dirname, 'code.js'), 'utf8');
const errors = [];
const sandbox = { figma, console: { error: (m) => errors.push(String(m)), log() {} }, setTimeout };
vm.createContext(sandbox);
vm.runInContext(code, sandbox, { filename: 'code.js' });

// main()은 비동기라 끝날 때까지 기다린다
const started = Date.now();
(function wait() {
  if (!closed && Date.now() - started < 10000) return setTimeout(wait, 20);
  const built = pages.filter((p) => p.name.startsWith('📐')).map((p) => `${p.name}: 화면 ${p._children[0] ? p._children[0].children.length - 1 : 0}개`);
  console.log(built.join('\n'));
  console.log(`노드: 프레임 ${counts.FRAME}, 텍스트 ${counts.TEXT}, 사각형 ${counts.RECTANGLE}, 원 ${counts.ELLIPSE}`);
  console.log(`알림: ${notified}`);
  if (!closed) errors.push('closePlugin이 호출되지 않음');
  if (errors.length > 0 || !String(notified).startsWith('✅')) {
    console.log('\n실패:');
    errors.forEach((e) => console.log(` - ${e}`));
    process.exit(1);
  }
  console.log('검증 통과');
})();
