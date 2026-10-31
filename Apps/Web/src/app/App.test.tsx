import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'
import { createServices } from './services'

describe('App', () => {
  it('로그인 전에는 로그인 화면을 보여 준다', () => {
    render(<App services={createServices('http://localhost:8080')} />)

    expect(screen.getByRole('heading', { name: 'KeyManage' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })
})
