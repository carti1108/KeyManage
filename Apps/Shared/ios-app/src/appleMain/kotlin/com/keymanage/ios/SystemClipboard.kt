package com.keymanage.ios

import com.keymanage.domain.clipboard.ClipboardWriter

/** 시스템 클립보드 쓰기. iOS는 UIPasteboard, macOS는 NSPasteboard */
internal expect fun systemClipboardWriter(): ClipboardWriter
