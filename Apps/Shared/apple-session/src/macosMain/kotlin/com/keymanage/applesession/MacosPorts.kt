package com.keymanage.applesession

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.clipboard.ClipboardWriter
import com.keymanage.domain.lock.BiometricError
import com.keymanage.domain.lock.BiometricKeyStore
import com.keymanage.feature.BackupFilesPort
import kotlinx.cinterop.BetaInteropApi
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSOpenPanel
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString
import platform.AppKit.NSSavePanel
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText

/**
 * macOS 포트. 기기 기록은 다른 앱과 섞이지 않게 Application Support 아래 앱 폴더에 둔다.
 * 생체 해제는 아직 열지 않는다: 생체 전용 키체인 항목은 서명한 앱의 키체인 권한이 있어야 만들 수 있다 (modularization-plan F단계 ④ macOS)
 */
fun macosPlatformPorts(): ApplePlatformPorts = ApplePlatformPorts(
    clipboardWriter = GeneralPasteboardClipboardWriter(),
    backupFiles = PanelBackupFiles(),
    biometricKeys = UnavailableBiometricKeyStore,
    deviceStore = FileDeviceRecordStore(directoryName = APP_FOLDER),
)

/**
 * 시스템 클립보드에 쓴다 (COM-001). 비울 때는 마지막으로 쓴 뒤 클립보드가 바뀌지 않았을 때만 비워,
 * 사용자가 다른 앱에서 복사한 값을 지우지 않는다. 메인 스레드에서만 부른다
 */
class GeneralPasteboardClipboardWriter(private val pasteboard: NSPasteboard = NSPasteboard.generalPasteboard) : ClipboardWriter {
    private var ownChangeCount: Long? = null

    override suspend fun write(text: String) {
        if (text.isEmpty()) {
            val mine = ownChangeCount ?: return
            ownChangeCount = null
            if (pasteboard.changeCount == mine) pasteboard.clearContents()
            return
        }
        pasteboard.clearContents()
        pasteboard.setString(text, forType = NSPasteboardTypeString)
        ownChangeCount = pasteboard.changeCount
    }
}

/** 백업 파일을 저장·열기 패널로 저장하고 고른다 (VLT-004). 사용자가 취소하면 아무것도 하지 않는다 */
@OptIn(BetaInteropApi::class)
class PanelBackupFiles : BackupFilesPort {
    /** @throws IllegalStateException 고른 위치에 쓰지 못함 */
    override suspend fun save(file: BackupFile) {
        val panel = NSSavePanel.savePanel()
        panel.nameFieldStringValue = file.name
        panel.allowedContentTypes = listOf(UTTypeJSON)
        if (panel.runModal() != NSModalResponseOK) return
        val url = panel.URL ?: return
        @Suppress("CAST_NEVER_SUCCEEDS")
        val written = (file.text as NSString).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!written) throw IllegalStateException("백업 파일을 쓰지 못함")
    }

    override suspend fun pick(): BackupFileSource? {
        val panel = NSOpenPanel.openPanel()
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.allowedContentTypes = listOf(UTTypeJSON, UTTypePlainText)
        if (panel.runModal() != NSModalResponseOK) return null
        val url = panel.URL ?: return null
        val attributes = url.path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }
        val size = (attributes?.get(NSFileSize) as? NSNumber)?.longValue?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
        return BackupFileSource(size) { readText(url) }
    }

    private fun readText(url: NSURL): String =
        NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = null)?.toString()
            ?: throw IllegalStateException("백업 파일을 열지 못함")
}

/** 생체 해제를 쓸 수 없는 기기. 설정에 스위치가 보이지 않고 잠금 화면에 버튼이 없다 */
object UnavailableBiometricKeyStore : BiometricKeyStore {
    override fun isAvailable(): Boolean = false
    override fun hasKey(): Boolean = false
    override suspend fun seal(key: ByteArray): Unit = throw BiometricError.Unavailable()
    override suspend fun open(): ByteArray = throw BiometricError.Invalidated()
    override fun clear() = Unit
}

private const val APP_FOLDER = "com.keymanage.macos"
