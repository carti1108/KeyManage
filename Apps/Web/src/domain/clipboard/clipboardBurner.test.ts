import { describe, expect, it } from 'vitest'
import type { Scheduler } from '../lock/autoLock'
import { BURN_AFTER_MS, createClipboardBurner, type ClipboardWriter } from './clipboardBurner'

function fakeScheduler() {
  let time = 0
  let tasks: { at: number; run: () => void; cancelled: boolean }[] = []
  const scheduler: Scheduler = {
    schedule(delayMs, run) {
      const task = { at: time + delayMs, run, cancelled: false }
      tasks.push(task)
      return () => {
        task.cancelled = true
      }
    },
  }
  return {
    scheduler,
    now: () => time,
    async advance(ms: number) {
      time += ms
      const due = tasks.filter((task) => !task.cancelled && task.at <= time)
      tasks = tasks.filter((task) => !due.includes(task))
      for (const task of due) task.run()
      await Promise.resolve()
      await Promise.resolve()
    },
  }
}

function fakeClipboard() {
  const writes: string[] = []
  let failing = false
  const clipboard: ClipboardWriter = {
    async write(text) {
      if (failing) throw new Error('문서에 포커스가 없음')
      writes.push(text)
    },
  }
  return {
    clipboard,
    writes,
    fail(value: boolean) {
      failing = value
    },
  }
}

function setup() {
  const time = fakeScheduler()
  const board = fakeClipboard()
  const burner = createClipboardBurner({ clipboard: board.clipboard, scheduler: time.scheduler, now: time.now })
  return { time, board, burner }
}

describe('clipboardBurner (COM-001)', () => {
  it('복사하고 정확히 30초 뒤에 빈 값으로 덮어쓴다', async () => {
    const { time, board, burner } = setup()

    await burner.copy('secret')
    expect(board.writes).toEqual(['secret'])
    expect(burner.state()).toEqual({ status: 'counting', expiresAt: BURN_AFTER_MS })

    await time.advance(BURN_AFTER_MS - 1)
    expect(board.writes).toEqual(['secret'])

    await time.advance(1)
    expect(board.writes).toEqual(['secret', ''])
    expect(burner.state()).toEqual({ status: 'idle' })
  })

  it('30초 안에 다시 복사하면 이전 타이머를 버리고 새로 잰다', async () => {
    const { time, board, burner } = setup()

    await burner.copy('first')
    await time.advance(20_000)
    await burner.copy('second')
    await time.advance(10_000)

    expect(board.writes).toEqual(['first', 'second'])

    await time.advance(20_000)
    expect(board.writes).toEqual(['first', 'second', ''])
  })

  it('복사에 실패하면 오류를 알리고 타이머를 시작하지 않는다', async () => {
    const { board, burner } = setup()
    board.fail(true)

    await expect(burner.copy('secret')).rejects.toThrow()

    expect(burner.state()).toEqual({ status: 'idle' })
  })

  it('30초에 비우지 못하면(백그라운드 탭) 다시 시도할 때 비운다', async () => {
    const { time, board, burner } = setup()
    await burner.copy('secret')

    board.fail(true)
    await time.advance(BURN_AFTER_MS)
    expect(burner.state()).toEqual({ status: 'pendingBurn' })

    board.fail(false)
    await burner.retry()

    expect(board.writes).toEqual(['secret', ''])
    expect(burner.state()).toEqual({ status: 'idle' })
  })

  it('비울 것이 없으면 다시 시도해도 클립보드를 건드리지 않는다', async () => {
    const { board, burner } = setup()

    await burner.retry()
    await burner.copy('secret')
    await burner.retry()

    expect(board.writes).toEqual(['secret'])
  })

  it('잠기면 30초를 기다리지 않고 바로 비운다', async () => {
    const { time, board, burner } = setup()
    await burner.copy('secret')

    await burner.clear()
    expect(board.writes).toEqual(['secret', ''])

    await time.advance(BURN_AFTER_MS)
    expect(board.writes).toEqual(['secret', ''])
  })

  it('잠길 때 비우지 못하면 돌아왔을 때 비운다', async () => {
    const { board, burner } = setup()
    await burner.copy('secret')

    board.fail(true)
    await burner.clear()
    expect(burner.state()).toEqual({ status: 'pendingBurn' })

    board.fail(false)
    await burner.retry()
    expect(board.writes).toEqual(['secret', ''])
  })

  it('복사한 적이 없으면 잠겨도 클립보드를 건드리지 않는다', async () => {
    const { board, burner } = setup()

    await burner.clear()

    expect(board.writes).toEqual([])
  })

  it('비우는 도중 새로 복사하면 새 값을 지우지 않는다', async () => {
    const { time, board, burner } = setup()
    let finishBurn = () => {}
    await burner.copy('first')
    // 비우기(빈 값 쓰기)가 늦게 끝나게 한다
    board.clipboard.write = (text) => {
      if (text === '') return new Promise<void>((resolve) => (finishBurn = () => (board.writes.push(''), resolve())))
      board.writes.push(text)
      return Promise.resolve()
    }

    await time.advance(BURN_AFTER_MS)
    await burner.copy('second')
    finishBurn()
    await Promise.resolve()

    expect(burner.state()).toEqual({ status: 'counting', expiresAt: 2 * BURN_AFTER_MS })
  })

  it('상태가 바뀌면 구독자에게 알린다', async () => {
    const { time, burner } = setup()
    const seen: string[] = []
    const off = burner.subscribe(() => seen.push(burner.state().status))

    await burner.copy('secret')
    await time.advance(BURN_AFTER_MS)
    off()
    await burner.copy('again')

    expect(seen).toEqual(['counting', 'idle'])
  })
})
