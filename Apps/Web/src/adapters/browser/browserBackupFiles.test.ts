import { afterEach, describe, expect, it, vi } from 'vitest'
import { REVOKE_AFTER_MS, browserBackupFiles } from './browserBackupFiles'

describe('browserBackupFiles (VLT-004)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('저장: Blob을 a[download]로 내려받고, 링크를 문서에서 떼고, 잠시 뒤 Blob 주소를 해제한다', async () => {
    vi.useFakeTimers()
    let blob: Blob | null = null
    const created = vi.fn((value: Blob) => ((blob = value), 'blob:backup'))
    const revoked = vi.fn()
    Object.assign(URL, { createObjectURL: created, revokeObjectURL: revoked })
    const clicked: HTMLAnchorElement[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.isConnected).toBe(true)
      clicked.push(this)
    })

    browserBackupFiles.save({ name: 'keymanage-backup-20261101.json', text: '{"format":"keymanage-backup"}' })

    expect(clicked).toHaveLength(1)
    expect(clicked[0].download).toBe('keymanage-backup-20261101.json')
    expect(clicked[0].getAttribute('href')).toBe('blob:backup')
    expect(clicked[0].isConnected).toBe(false)
    expect(blob).not.toBeNull()
    expect(blob!.type).toBe('application/json')
    expect(await blob!.text()).toBe('{"format":"keymanage-backup"}')
    expect(revoked).not.toHaveBeenCalled()

    vi.advanceTimersByTime(REVOKE_AFTER_MS)
    expect(revoked).toHaveBeenCalledWith('blob:backup')
  })

  it('고르기: JSON 파일 선택 창을 열고 고른 파일을 돌려준다', async () => {
    const inputs: HTMLInputElement[] = []
    vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(function (this: HTMLInputElement) {
      inputs.push(this)
    })

    const picking = browserBackupFiles.pick()
    const [input] = inputs
    expect(input.type).toBe('file')
    expect(input.accept).toBe('.json,application/json')
    const file = new File(['{}'], 'keymanage-backup-20261101.json', { type: 'application/json' })
    Object.defineProperty(input, 'files', { value: [file] })
    input.dispatchEvent(new Event('change'))

    await expect(picking).resolves.toBe(file)
  })

  it('고르기: 선택 창을 취소하면 null', async () => {
    const inputs: HTMLInputElement[] = []
    vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(function (this: HTMLInputElement) {
      inputs.push(this)
    })

    const picking = browserBackupFiles.pick()
    inputs[0].dispatchEvent(new Event('cancel'))

    await expect(picking).resolves.toBeNull()
  })

  it('고르기: 창으로 포커스가 돌아와도 시간으로 취소를 짐작하지 않고 늦게 온 change를 기다린다', async () => {
    vi.useFakeTimers()
    const inputs: HTMLInputElement[] = []
    vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(function (this: HTMLInputElement) {
      inputs.push(this)
    })
    let settled = false
    const picking = browserBackupFiles.pick().finally(() => (settled = true))

    window.dispatchEvent(new Event('focus'))
    vi.advanceTimersByTime(60_000)
    await Promise.resolve()
    expect(settled).toBe(false)

    const file = new File(['{}'], 'late.json')
    Object.defineProperty(inputs[0], 'files', { value: [file] })
    inputs[0].dispatchEvent(new Event('change'))
    await expect(picking).resolves.toBe(file)
  })
})
