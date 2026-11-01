// IndexedDB에 한 계정의 기기 기록을 둔다 (ADR-0019)
// localStorage는 문자열만 담고 용량 한도가 5MB 안팎이라 5MB 볼트(VLT-001)를 담지 못할 수 있어 쓰지 않는다
import { DeviceStorageError, type DeviceRecord, type DeviceStore } from '../../domain/device/deviceRecord'

const DATABASE_NAME = 'keymanage'
const DATABASE_VERSION = 1
const STORE_NAME = 'device'
// 기기에는 한 계정만 두므로 키는 하나다
const RECORD_KEY = 'account'
/** 열기가 끝나지 않으면(일부 브라우저의 멈춤, 다른 탭의 차단) 실패로 보고 로그인 화면에서 시작한다 */
export const OPEN_TIMEOUT_MS = 3_000

function promised<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function openDatabase(factory: IDBFactory, onClosed: () => void): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    let settled = false
    const finish = (outcome: () => void) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      outcome()
    }
    const timer = setTimeout(() => finish(() => reject(new Error('기기 저장소를 여는 데 시간이 너무 걸림'))), OPEN_TIMEOUT_MS)
    const request = factory.open(DATABASE_NAME, DATABASE_VERSION)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE_NAME)) request.result.createObjectStore(STORE_NAME)
    }
    request.onblocked = () => finish(() => reject(new Error('다른 탭이 기기 저장소의 이전 버전을 쓰고 있음')))
    request.onerror = () => finish(() => reject(request.error))
    request.onsuccess = () => {
      const db = request.result
      // 제한 시간이 지난 뒤에 열렸으면 쓰지 않으니 바로 닫는다
      if (settled) {
        db.close()
        return
      }
      // 다른 탭이 새 버전으로 올리려 하면 막지 않도록 닫고, 다음 쓰기 때 다시 연다
      db.onversionchange = () => {
        db.close()
        onClosed()
      }
      finish(() => resolve(db))
    }
  })
}

/** 쓰기는 트랜잭션이 끝나야(디스크에 반영돼야) 성공으로 본다 */
function completed(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
  })
}

/**
 * 브라우저가 저장 공간이 부족할 때 기기 기록을 지우지 않도록 요청한다 (ADR-0019).
 * 브라우저가 거절하거나 지원하지 않아도 기록은 쓸 수 있으므로 결과를 기다리지 않는다
 */
export function requestPersistentStorage(): void {
  globalThis.navigator?.storage?.persist?.().catch(() => {
    // 요청 자체가 실패해도(일부 웹뷰) 기기 저장은 그대로 동작한다. 지워질 수 있다는 위험만 남는다
  })
}

export function createIndexedDbDeviceStore(factory: IDBFactory = globalThis.indexedDB): DeviceStore {
  let database: Promise<IDBDatabase> | null = null

  async function connection(): Promise<IDBDatabase> {
    try {
      database ??= openDatabase(factory, () => (database = null))
      return await database
    } catch (error) {
      // 열기에 실패했으면 다음 호출에서 다시 연다
      database = null
      throw new DeviceStorageError(error)
    }
  }

  async function run<T>(work: (db: IDBDatabase) => Promise<T>): Promise<T> {
    const db = await connection()
    try {
      return await work(db)
    } catch (error) {
      // 트랜잭션 실패(저장 공간 부족 등)는 연결을 버리지 않는다. 열린 연결이 새지 않게 한다
      throw new DeviceStorageError(error)
    }
  }

  async function write(change: (store: IDBObjectStore) => void): Promise<void> {
    await run((db) => {
      const transaction = db.transaction(STORE_NAME, 'readwrite')
      change(transaction.objectStore(STORE_NAME))
      return completed(transaction)
    })
  }

  return {
    load: () => run((db) => promised(db.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(RECORD_KEY))),
    save: (record: DeviceRecord) => write((store) => store.put(record, RECORD_KEY)),
    clear: () => write((store) => store.delete(RECORD_KEY)),
  }
}
