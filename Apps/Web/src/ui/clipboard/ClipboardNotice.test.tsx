import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFakeClipboard } from '../../test/fakeClipboard'
import { ClipboardNotice } from './ClipboardNotice'

describe('ClipboardNotice', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('복사하기 전에는 아무것도 보이지 않는다', () => {
    const { clipboard, now } = createFakeClipboard()
    render(<ClipboardNotice clipboard={clipboard} now={now} />)

    expect(screen.queryByText('클립보드 보안 소각')).not.toBeInTheDocument()
  })

  it('복사하면 정책 문구와 남은 초를 보이고, 1초마다 줄어든다', async () => {
    const fake = createFakeClipboard()
    render(<ClipboardNotice clipboard={fake.clipboard} now={fake.now} />)

    await act(() => fake.clipboard.copy('secret'))

    expect(screen.getByText('클립보드 보안 소각')).toBeInTheDocument()
    // 아이디도 복사하므로 비밀번호로 한정하지 않는다
    expect(screen.getByText('보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.')).toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveAccessibleName('클립보드 자동 삭제까지 30초')

    act(() => {
      fake.elapse(1_000)
      vi.advanceTimersByTime(1_000)
    })

    expect(screen.getByRole('timer')).toHaveAccessibleName('클립보드 자동 삭제까지 29초')
  })

  it('알림 영역은 복사하기 전부터 있어서 안내가 생기면 읽힌다', async () => {
    const fake = createFakeClipboard()
    render(<ClipboardNotice clipboard={fake.clipboard} now={fake.now} />)
    const region = screen.getByRole('status')
    expect(region).toBeEmptyDOMElement()

    await act(() => fake.clipboard.copy('secret'))

    expect(region).toHaveTextContent('클립보드 보안 소각')
  })

  it('비우지 못했으면 알리고, 직접 비우는 버튼을 준다', async () => {
    const fake = createFakeClipboard()
    render(<ClipboardNotice clipboard={fake.clipboard} now={fake.now} />)
    await act(() => fake.clipboard.copy('secret'))
    fake.writer.write.mockRejectedValueOnce(new Error('문서에 포커스가 없음'))

    await act(async () => fake.elapse(30_000, true))

    expect(screen.getByText('클립보드를 아직 비우지 못했습니다. 이 화면을 누르면 다시 비웁니다.')).toBeInTheDocument()
    await act(async () => screen.getByRole('button', { name: '지금 비우기' }).click())

    expect(fake.writes).toEqual(['secret', ''])
    expect(screen.queryByRole('button', { name: '지금 비우기' })).not.toBeInTheDocument()
  })

  it('비우고 나면 사라진다', async () => {
    const fake = createFakeClipboard()
    render(<ClipboardNotice clipboard={fake.clipboard} now={fake.now} />)
    await act(() => fake.clipboard.copy('secret'))

    await act(async () => fake.elapse(30_000, true))

    expect(screen.queryByText('클립보드 보안 소각')).not.toBeInTheDocument()
    expect(fake.writes).toEqual(['secret', ''])
  })
})
