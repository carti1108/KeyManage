// 화면이 쓰는 클립보드 기능만 추린 포트 (ADR-0004). app이 소각 타이머가 붙은 실제 클립보드를 넘긴다
import type { ClipboardBurner } from '../../domain/clipboard/clipboardBurner'

export type ClipboardPort = Pick<ClipboardBurner, 'copy' | 'state' | 'subscribe'>
