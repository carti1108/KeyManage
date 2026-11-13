// 어댑터를 만들어 domain 유스케이스에 끼운다. 화면에는 이 결과만 넘긴다 (ADR-0004, ADR-0013)
import { browserScheduler } from '../adapters/browser/autoLockEvents'
import { browserBackupFiles } from '../adapters/browser/browserBackupFiles'
import { browserClipboard } from '../adapters/browser/browserClipboard'
import { createBrowserTabs } from '../adapters/browser/browserTabs'
import { createIndexedDbDeviceStore } from '../adapters/browser/indexedDbDeviceStore'
import { createLocalStorageAutoLockSetting } from '../adapters/browser/localStorageAutoLockSetting'
import { createSessionStorageTabMemory } from '../adapters/browser/sessionStorageTabMemory'
import * as kotlinSessionModule from 'keymanage-web-session'
import { createKotlinSession } from '../adapters/kotlin/kotlinSession'
import { createClipboardBurner, type ClipboardBurner } from '../domain/clipboard/clipboardBurner'
import type { DeviceStore } from '../domain/device/deviceRecord'
import type { TabMemory } from '../domain/device/tabMemory'
import type { AutoLockSettingStore } from '../domain/lock/autoLockSetting'
import type { BackupFiles } from '../domain/backup/backupFile'
import type { VaultSession } from '../domain/session/vaultSession'
import type { Tabs } from '../domain/tabs/tabs'

export interface Services {
  /** 로그인·가입·잠금·잠금 해제·로그아웃과 볼트 저장 (SEC-002, SEC-003) */
  readonly session: VaultSession
  /** 복사하고 30초 뒤 비우는 클립보드 (COM-001) */
  readonly clipboard: ClipboardBurner
  /** 같은 브라우저의 다른 탭과 나누는 잠금·알림 (ADR-WEB-001) */
  readonly tabs: Tabs
  /** 기기마다 저장하는 자동 잠금 시간. 계정과 묶지 않는다 (SEC-003, ADR-WEB-002) */
  readonly autoLockSetting: AutoLockSettingStore
  /** 암호화 백업 파일 저장(다운로드)·고르기 (VLT-004) */
  readonly backupFiles: BackupFiles
}

/**
 * @param deviceStore 한 계정의 기록을 두는 기기 저장소 (ADR-0019). 기본은 브라우저 IndexedDB
 * @param tabs 같은 브라우저의 다른 탭과 나누는 잠금·알림 (ADR-WEB-001). 기본은 navigator.locks·BroadcastChannel
 * @param autoLockSetting 자동 잠금 시간 저장소 (ADR-WEB-002). 기본은 localStorage
 * @param tabMemory 이 탭이 연 계정의 KDF 값 기억 (SEC-003 규칙 11). 기본은 sessionStorage
 * @throws InvalidBaseUrlError 빌드할 때 넣은 서버 주소가 규칙에 맞지 않을 때
 */
export function createServices(
  apiBaseUrl: string | undefined = import.meta.env.VITE_API_BASE_URL,
  deviceStore: DeviceStore = createIndexedDbDeviceStore(),
  tabs: Tabs = createBrowserTabs(),
  autoLockSetting: AutoLockSettingStore = createLocalStorageAutoLockSetting(),
  tabMemory: TabMemory = createSessionStorageTabMemory(),
): Services {
  return {
    // 세션은 공유 Kotlin 도메인이 맡는다. 서버 주소 검사와 HTTP도 Kotlin(server-http)이 한다 (domain-migration-plan 10장)
    session: createKotlinSession(kotlinSessionModule, { apiBaseUrl, deviceStore, tabs, tabMemory }),
    clipboard: createClipboardBurner({ clipboard: browserClipboard, scheduler: browserScheduler, now: () => Date.now() }),
    tabs,
    autoLockSetting,
    backupFiles: browserBackupFiles,
  }
}
