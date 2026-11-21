package com.keymanage.applesession

import com.keymanage.domain.clipboard.BURN_AFTER
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.UIKit.UIViewController

/**
 * iOS 포트. 복사한 값은 이 기기에만 두고 iOS가 30초 뒤 비우게 하며, 기기 기록은 기기가 잠긴 동안 읽지 못하게 보호한다
 *
 * @param presenter 문서 선택 창을 띄울 화면
 */
fun iosPlatformPorts(presenter: () -> UIViewController?): ApplePlatformPorts = ApplePlatformPorts(
    clipboardWriter = PasteboardClipboardWriter(lifetime = BURN_AFTER),
    backupFiles = DocumentPickerBackupFiles(presenter),
    biometricKeys = KeychainBiometricKeyStore(),
    deviceStore = FileDeviceRecordStore(writingOptions = NSDataWritingFileProtectionComplete),
)
