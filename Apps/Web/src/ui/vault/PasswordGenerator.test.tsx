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

  it.each(['7', '65', '', '1e1', '12.5'])('길이 %s는 사용을 막고, 입력을 마치면 정책 문구를 보인다', (length) => {
    const { generate } = setup()

    fireEvent.change(screen.getByLabelText('길이'), { target: { value: length } })

    // 입력하는 도중(16 → 2 → 20)에는 경고를 깜빡이지 않는다
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이 비밀번호 사용' })).toBeDisabled()
    // 옵션과 맞지 않는 이전 결과는 보이지 않는다
    expect(screen.queryByTestId('generated-password')).not.toBeInTheDocument()
    expect(screen.queryByRole('meter')).not.toBeInTheDocument()

    fireEvent.blur(screen.getByLabelText('길이'))

    expect(screen.getByRole('alert')).toHaveTextContent('비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.')
    expect(generate).toHaveBeenCalledOnce()
  })

  it('잘못된 길이를 고치면 경고가 사라지고 새로 만든다', () => {
    const { generate } = setup()
    const length = screen.getByLabelText('길이')

    fireEvent.change(length, { target: { value: '7' } })
    fireEvent.blur(length)
    fireEvent.change(length, { target: { value: '20' } })

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이 비밀번호 사용' })).toBeEnabled()
    expect(generate).toHaveBeenLastCalledWith(expect.objectContaining({ length: 20 }))
    expect(screen.getByTestId('generated-password')).toHaveTextContent('pw2-20')
  })

  it('문자군을 모두 끄면 정책 문구를 보이고 사용을 막는다', () => {
    setup()

    for (const label of ['대문자', '소문자', '숫자', '특수기호']) fireEvent.click(screen.getByLabelText(label))

    expect(screen.getByRole('alert')).toHaveTextContent('최소 1개 이상의 문자 종류를 선택해야 합니다.')
    expect(screen.getByRole('button', { name: '이 비밀번호 사용' })).toBeDisabled()
  })

  it('강도를 항목 비밀번호와 구분되는 이름으로 보여 준다', () => {
    setup()

    expect(screen.getByRole('meter', { name: '생성된 비밀번호 강도' })).toBeInTheDocument()
  })

  it('열면 길이 칸에 포커스를 둔다', () => {
    setup()

    expect(screen.getByLabelText('길이')).toHaveFocus()
  })

  it('새로 만들면 보조 기술에 알린다', () => {
    setup()
    expect(screen.getByRole('status')).toHaveTextContent('')

    fireEvent.click(screen.getByRole('button', { name: '다시 생성' }))

    expect(screen.getByRole('status')).toHaveTextContent('새 비밀번호를 만들었습니다.')
  })

  it('닫을 수 있다', () => {
    const { onClose } = setup()

    fireEvent.click(screen.getByRole('button', { name: '닫기' }))

    expect(onClose).toHaveBeenCalledOnce()
  })
})
