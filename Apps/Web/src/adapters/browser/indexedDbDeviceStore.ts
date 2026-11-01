// IndexedDB에 한 계정의 기기 기록을 둔다 (ADR-0019)
// localStorage는 문자열만 담고 용량 한도가 5MB 안팎이라 5MB 볼트(VLT-001)를 담지 못할 수 있어 쓰지 않는다
import { DeviceStorageError, type DeviceRecord, type DeviceStore } from '../../domain/device/deviceRecord'

const DATABASE_NAME = 'keymanage'
const DATABASE_VERSION = 1
const STORE_NAME = 'device'
// 기기에는 한 계정만 두므로 키는 하나다
const RECORD_KEY = 'account'

function promised<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function openDatabase(factory: IDBFactory): Promise<IDBDatabase> {
  const request = factory.open(DATABASE_NAME, DATABASE_VERSION)
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains(STORE_NAME)) request.result.createObjectStore(STORE_NAME)
  }
  return promised(request)
}

/** 쓰기는 트랜잭션이 끝나야(디스크에 반영돼야) 성공으로 본다 */
function completed(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
  })
}

export function createIndexedDbDeviceStore(factory: IDBFactory = globalThis.indexedDB): DeviceStore {
  let database: Promise<IDBDatabase> | null = null

  async function run<T>(work: (db: IDBDatabase) => Promise<T>): Promise<T> {
    try {
      database ??= openDatabase(factory)
      return await work(await database)
    } catch (error) {
      // 열기에 실패했으면 다음 호출에서 다시 연다
      database = null
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
