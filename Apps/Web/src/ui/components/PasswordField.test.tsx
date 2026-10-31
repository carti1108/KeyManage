import { act, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PasswordField, REMASK_AFTER_MS, type PasswordFieldProps } from './PasswordField'

function setup(props: Partial<PasswordFieldProps> = {}) {
  const onTextChange = vi.fn()
  const onCopy = vi.fn()
  const base: PasswordFieldProps = { label: '비밀번호', value: 's3cret', onTextChange, onCopy, ...props }
  const view = render(<PasswordField {...base} />)
  const rerender = (next: Partial<PasswordFieldProps>) => view.rerender(<PasswordField {...base} {...next} />)
  return { onTextChange, onCopy, rerender, input: screen.getByLabelText('비밀번호') }
}

// 실제 화면처럼 입력값을 상태로 들고 있는 부모
function ControlledField() {
  const [value, setValue] = useState('s3cret')
  return <PasswordField label="비밀번호" value={value} onTextChange={setValue} />
}

describe('PasswordField', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('기본은 가려져 있고 기본 안내 문구를 보인다', () => {
    const { input } = setup({ value: '' })

    expect(input).toHaveAttribute('type', 'password')
    expect(input).toHaveAttribute('placeholder', '비밀번호 입력')
    expect(input).toHaveAttribute('spellcheck', 'false')
  })

  it('입력하면 바뀐 값을 알린다', () => {
    const { input, onTextChange } = setup()

    fireEvent.change(input, { target: { value: 'next' } })

    expect(onTextChange).toHaveBeenCalledWith('next')
  })

  it('보기 버튼으로 평문과 가림을 오간다', () => {
    const { input } = setup()

    fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))
    expect(input).toHaveAttribute('type', 'text')

    fireEvent.click(screen.getByRole('button', { name: '비밀번호 숨기기' }))
    expect(input).toHaveAttribute('type', 'password')
  })

  it('평문으로 둔 채 10초간 조작이 없으면 다시 가린다', () => {
    const { input } = setup()
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    act(() => vi.advanceTimersByTime(REMASK_AFTER_MS - 1))
    expect(input).toHaveAttribute('type', 'text')

    act(() => vi.advanceTimersByTime(1))
    expect(input).toHaveAttribute('type', 'password')
  })

  it('입력하면 다시 가리기까지의 시간을 처음부터 다시 잰다', () => {
    render(<ControlledField />)
    const input = screen.getByLabelText('비밀번호')
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    act(() => vi.advanceTimersByTime(REMASK_AFTER_MS - 1))
    fireEvent.change(input, { target: { value: 'typing' } })
    act(() => vi.advanceTimersByTime(REMASK_AFTER_MS - 1))

    expect(input).toHaveAttribute('type', 'text')

    act(() => vi.advanceTimersByTime(1))
    expect(input).toHaveAttribute('type', 'password')
  })

  it('값이 바뀌면(생성기 재생성 등) 다시 가리기까지의 시간을 처음부터 다시 잰다', () => {
    const { input, rerender } = setup()
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    act(() => vi.advanceTimersByTime(REMASK_AFTER_MS - 1))
    rerender({ value: 'regenerated' })
    act(() => vi.advanceTimersByTime(REMASK_AFTER_MS - 1))
    expect(input).toHaveAttribute('type', 'text')

    act(() => vi.advanceTimersByTime(1))
    expect(input).toHaveAttribute('type', 'password')
  })

  it('숨겼다가 다시 보면 처음부터 다시 잰다', () => {
    const { input } = setup()
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))
    act(() => vi.advanceTimersByTime(REMASK_AFTER_MS - 1))
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 숨기기' }))
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    act(() => vi.advanceTimersByTime(REMASK_AFTER_MS - 1))
    expect(input).toHaveAttribute('type', 'text')
  })

  it('평문으로 보이는 동안에는 브라우저 입력 기록에 남지 않게 자동완성을 끈다', () => {
    const { input } = setup({ autoComplete: 'current-password' })
    expect(input).toHaveAttribute('autocomplete', 'current-password')

    fireEvent.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    expect(input).toHaveAttribute('autocomplete', 'off')
  })

  it('맞춤법 검사 같은 보안 속성은 호출하는 쪽이 덮어쓰지 못한다', () => {
    const { input } = setup({ spellCheck: true })

    expect(input).toHaveAttribute('spellcheck', 'false')
  })

  it('복사 버튼을 누르면 onCopy를 부른다', () => {
    const { onCopy } = setup()

    fireEvent.click(screen.getByRole('button', { name: '비밀번호 복사' }))

    expect(onCopy).toHaveBeenCalledOnce()
  })

  it('복사 콜백이 없으면 복사 버튼을 그리지 않는다', () => {
    setup({ onCopy: undefined })

    expect(screen.queryByRole('button', { name: '비밀번호 복사' })).not.toBeInTheDocument()
  })

  it('복사 버튼을 끌 수 있다', () => {
    setup({ showCopyAction: false })

    expect(screen.queryByRole('button', { name: '비밀번호 복사' })).not.toBeInTheDocument()
  })
})
