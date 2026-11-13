// 조립 지점(Composition Root): 브라우저 어댑터를 JS 포트로 바꿔 공유 RIB 앱(WebKeyManageApp)을 만든다 (ADR-0004, domain-migration-plan 11.4)
// 화면 로직은 Kotlin RIB에 있고, 여기서는 포트를 잇고 앱 배선(*Wiring.ts)이 쓸 세션을 감싸는 일만 한다
import type { JsBackupFiles, JsClipboard, JsEnvironment, WebKeyManageApp } from 'keymanage-web-session'
import { browserBackupFiles } from '../adapters/browser/browserBackupFiles'
import { browserScheduler } from '../adapters/browser/autoLockEvents'
import { browserClipboard } from '../adapters/browser/browserClipboard'
import { createBrowserTabs } from '../adapters/browser/browserTabs'
import { createIndexedDbDeviceStore } from '../adapters/browser/indexedDbDeviceStore'
import { createLocalStorageAutoLockSetting } from '../adapters/browser/localStorageAutoLockSetting'
import { isNetworkOnline, subscribeNetworkStatus } from '../adapters/browser/networkStatus'
import { createSessionStorageTabMemory } from '../adapters/browser/sessionStorageTabMemory'
import { toJsDeviceStore, toJsTabMemory, toJsTabs } from '../adapters/kotlin/kotlinPorts'
import { wrapKotlinSession } from '../adapters/kotlin/kotlinSession'
import { requestPersistentStorage } from '../adapters/browser/indexedDbDeviceStore'
import { MasterPasswordChangedError } from '../domain/auth/authService'
import { SessionExpiredError } from '../domain/session/sessionTypes'
import { describeError } from '../ui/messages/errorMessage'
import type { BackupFiles } from '../domain/backup/backupFile'
import { createClipboardBurner, type ClipboardBurner } from '../domain/clipboard/clipboardBurner'
import type { DeviceStore } from '../domain/device/deviceRecord'
import type { TabMemory } from '../domain/device/tabMemory'
import type { AutoLockSettingStore } from '../domain/lock/autoLockSetting'
import type { VaultSession } from '../domain/session/sessionTypes'
import type { Tabs } from '../domain/tabs/tabs'

/** Kotlin 빌드 결과에서 이 파일이 쓰는 값. 테스트는 가짜를 넣는다 */
export interface KotlinAppModule {
  WebKeyManageApp: new (
    apiBaseUrl: string | null,
    deviceStore: ReturnType<typeof toJsDeviceStore>,
    tabs: ReturnType<typeof toJsTabs>,
    tabMemory: ReturnType<typeof toJsTabMemory> | null,
    clipboard: JsClipboard,
    backupFiles: JsBackupFiles,
    environment: JsEnvironment,
  ) => WebKeyManageApp
  errorInfo(error: unknown): ReturnType<Parameters<typeof wrapKotlinSession>[0]['errorInfo']>
}

export interface KotlinAppDeps {
  readonly apiBaseUrl?: string
  readonly deviceStore?: DeviceStore
  readonly tabs?: Tabs
  readonly autoLockSetting?: AutoLockSettingStore
  readonly tabMemory?: TabMemory
  readonly backupFiles?: BackupFiles
}

export interface KotlinApp {
  /** RIB 트리. main.tsx가 React 밖에서 start()한다 */
  readonly app: WebKeyManageApp
  /** 앱 배선이 쓰는 같은 세션 */
  readonly session: VaultSession
  readonly tabs: Tabs
  readonly clipboard: ClipboardBurner
  /** 이 탭에서 바꾼 값도 구독자에게 알리는 자동 잠금 설정 (Kotlin 설정 화면이 쓰고 React 배선이 따라간다) */
  readonly autoLockSetting: AutoLockSettingStore
}

/** 이 탭의 write도 구독자에게 알린다. Kotlin 설정 화면이 바꾼 값을 React 자동 잠금 타이머가 따라가게 한다 */
export function observeLocalWrites(store: AutoLockSettingStore): AutoLockSettingStore {
  const listeners = new Set<(timeoutMs: number) => void>()
  return {
    read: () => store.read(),
    write(timeoutMs) {
      store.write(timeoutMs)
      for (const listener of [...listeners]) listener(store.read())
    },
    subscribe(listener) {
      listeners.add(listener)
      const off = store.subscribe(listener)
      return () => {
        listeners.delete(listener)
        off()
      }
    },
  }
}

/**
 * @throws InvalidBaseUrlError 빌드할 때 넣은 서버 주소가 규칙에 맞지 않을 때
 */
export function createKotlinApp(kotlin: KotlinAppModule, deps: KotlinAppDeps = {}): KotlinApp {
  const apiBaseUrl = deps.apiBaseUrl ?? import.meta.env.VITE_API_BASE_URL
  const tabs = deps.tabs ?? createBrowserTabs()
  const autoLockSetting = observeLocalWrites(deps.autoLockSetting ?? createLocalStorageAutoLockSetting())
  const backupFiles = deps.backupFiles ?? browserBackupFiles
  const clipboard = createClipboardBurner({ clipboard: browserClipboard, scheduler: browserScheduler, now: () => Date.now() })

  const jsClipboard: JsClipboard = { copy: (text) => clipboard.copy(text) as never }
  const jsBackupFiles: JsBackupFiles = {
    save: async (name, text) => backupFiles.save({ name, text }) as never,
    pick: () => backupFiles.pick() as never,
  }
  const environment: JsEnvironment = {
    serverAddress: apiBaseUrl ?? '',
    isNetworkOffline: () => !isNetworkOnline(),
    subscribeNetwork: subscribeNetworkStatus,
    readAutoLock: () => autoLockSetting.read(),
    writeAutoLock: (ms) => autoLockSetting.write(ms),
    subscribeAutoLock: (listener) => autoLockSetting.subscribe(listener),
  }

  const app = new kotlin.WebKeyManageApp(
    apiBaseUrl ?? null,
    toJsDeviceStore(deps.deviceStore ?? createIndexedDbDeviceStore()),
    toJsTabs(tabs),
    toJsTabMemory(deps.tabMemory ?? createSessionStorageTabMemory()),
    jsClipboard,
    jsBackupFiles,
    environment,
  )
  return { app, session: wrapKotlinSession(kotlin, app.session as never), tabs, clipboard, autoLockSetting }
}

/** 로그인 화면으로 돌려보내며 이유를 알려야 하는 복원 오류 (SEC-002, SEC-003 규칙 11) */
function signInNoticeOf(error: unknown): string | null {
  if (error instanceof SessionExpiredError || error instanceof MasterPasswordChangedError) return describeError(error).body
  return null
}

/**
 * 기기 기록을 읽은 뒤 첫 화면을 붙인다. 기록이 있으면 잠금 화면으로, 읽지 못하면 로그인 화면으로 시작한다 (SEC-003, ADR-0019, VLT-003).
 * React StrictMode의 이중 마운트와 겹치지 않도록 React 밖(main.tsx)에서 한 번 부른다 (carti ADR-0009)
 */
export async function bootKotlinApp(kotlinApp: Pick<KotlinApp, 'app' | 'session'>): Promise<void> {
  requestPersistentStorage()
  let notice: string | null = null
  try {
    await kotlinApp.session.restore()
  } catch (error) {
    notice = signInNoticeOf(error)
  }
  kotlinApp.app.start(notice)
}
