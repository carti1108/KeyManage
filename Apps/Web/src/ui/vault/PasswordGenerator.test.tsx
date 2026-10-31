import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { GeneratorOptions } from '../../domain/generator/passwordGenerator'
import { PasswordGenerator } from './PasswordGenerator'

function setup() {
  let count = 0
  const generate = vi.fn((options: GeneratorOptions) => `pw${++count}-${options.length}`)
  const onUse = vi.fn()
  const onClose = vi.fn()
  render(<PasswordGenerator generate={generate} onUse={onUse} onClose={onClose} />)
  return { generate, onUse, onClose }
}

describe('PasswordGenerator', () => {
  it('열면 기본 옵션(16자, 네 문자군)으로 하나 만들어 보여 준다', () => {
    const { generate } = setup()

    expect(generate).toHaveBeenCalledWith({ length: 16, uppercase: true, lowercase: true, digits: true, symbols: true })
    expect(screen.getByTestId('generated-password')).toHaveTextContent('pw1-16')
    expect(screen.getByLabelText('길이')).toHaveValue(16)
    for (const label of ['대문자', '소문자', '숫자', '특수기호']) expect(screen.getByLabelText(label)).toBeChecked()
  })

  it('옵션을 바꾸면 새로 만든다', () => {
    const { generate } = setup()

    fireEvent.change(screen.getByLabelText('길이'), { target: { value: '20' } })
    fireEvent.click(screen.getByLabelText('특수기호'))

    expect(generate).toHaveBeenLastCalledWith({ length: 20, uppercase: true, lowercase: true, digits: true, symbols: false })
    expect(screen.getByTestId('generated-password')).toHaveTextContent('pw3-20')
  })

  it('다시 생성을 누르면 같은 옵션으로 새로 만든다', () => {
    setup()

    fireEvent.click(screen.getByRole('button', { name: '다시 생성' }))

    expect(screen.getByTestId('generated-password')).toHaveTextContent('pw2-16')
  })

  it('사용을 누르면 지금 보이는 비밀번호를 넘긴다', () => {
    const { onUse } = setup()

    fireEvent.click(screen.getByRole('button', { name: '이 비밀번호 사용' }))

    expect(onUse).toHaveBeenCalledWith('pw1-16')
  })

  it.each(['7', '65', ''])('길이 %s는 정책 문구를 보이고 사용을 막는다', (length) => {
    const { generate } = setup()

    fireEvent.change(screen.getByLabelText('길이'), { target: { value: length } })

    expect(screen.getByRole('alert')).toHaveTextContent('비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.')
    expect(screen.getByRole('button', { name: '이 비밀번호 사용' })).toBeDisabled()
    expect(generate).toHaveBeenCalledOnce()
  })

  it('문자군을 모두 끄면 정책 문구를 보이고 사용을 막는다', () => {
    setup()

    for (const label of ['대문자', '소문자', '숫자', '특수기호']) fireEvent.click(screen.getByLabelText(label))

    expect(screen.getByRole('alert')).toHaveTextContent('최소 1개 이상의 문자 종류를 선택해야 합니다.')
    expect(screen.getByRole('button', { name: '이 비밀번호 사용' })).toBeDisabled()
  })

  it('강도를 보여 준다', () => {
    setup()

    expect(screen.getByRole('meter', { name: '비밀번호 강도' })).toBeInTheDocument()
  })

  it('닫을 수 있다', () => {
    const { onClose } = setup()

    fireEvent.click(screen.getByRole('button', { name: '닫기' }))

    expect(onClose).toHaveBeenCalledOnce()
  })
})
