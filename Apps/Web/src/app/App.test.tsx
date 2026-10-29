import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('KeyManage 제목을 보여 준다', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'KeyManage' })).toBeInTheDocument()
  })
})
