// 비밀번호 생성기 (GEN-001). 만든 비밀번호는 사용을 누르기 전까지 어디에도 저장하지 않는다
import { RefreshCw } from 'lucide-react'
import { useId, useState, type KeyboardEvent } from 'react'
import {
  DEFAULT_GENERATOR_OPTIONS,
  checkGeneratorOptions,
  generatePassword,
  type GeneratorOptions,
} from '../../domain/generator/passwordGenerator'
import { scorePassword } from '../../domain/generator/passwordStrength'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { EntropyGauge } from '../components/EntropyGauge'

const PROBLEM_MESSAGES = {
  length: '비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.',
  noCharset: '최소 1개 이상의 문자 종류를 선택해야 합니다.',
} as const

const CHARSETS = [
  ['uppercase', '대문자'],
  ['lowercase', '소문자'],
  ['digits', '숫자'],
  ['symbols', '특수기호'],
] as const

type Charsets = Omit<GeneratorOptions, 'length'>

export interface PasswordGeneratorProps {
  onUse: (password: string) => void
  onClose: () => void
  /** 테스트에서 결과를 고정할 때만 바꾼다 */
  generate?: (options: GeneratorOptions) => string
}

export function PasswordGenerator({ onUse, onClose, generate = generatePassword }: PasswordGeneratorProps) {
  // 입력 중에 비울 수 있도록 길이는 글자로 들고 있는다
  const [length, setLength] = useState(String(DEFAULT_GENERATOR_OPTIONS.length))
  const [charsets, setCharsets] = useState<Charsets>(DEFAULT_GENERATOR_OPTIONS)
  const [password, setPassword] = useState(() => generate(DEFAULT_GENERATOR_OPTIONS))
  const [problem, setProblem] = useState<keyof typeof PROBLEM_MESSAGES | null>(null)
  const lengthId = useId()

  function regenerate(nextLength: string, nextCharsets: Charsets) {
    const options = { ...nextCharsets, length: nextLength.trim() === '' ? Number.NaN : Number(nextLength) }
    const found = checkGeneratorOptions(options)
    setProblem(found)
    if (found === null) setPassword(generate(options))
  }

  // 바깥 항목 폼은 Enter를 저장으로 처리한다. 생성기 안의 Enter는 저장으로 넘기지 않는다
  function stopEnter(event: KeyboardEvent) {
    if (event.key === 'Enter') event.stopPropagation()
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/40 p-3" onKeyDown={stopEnter}>
      <p data-testid="generated-password" className="break-all font-mono text-sm">
        {password}
      </p>
      <EntropyGauge score={scorePassword(password)} />
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        <label htmlFor={lengthId} className="flex items-center gap-2">
          길이
          <input
            id={lengthId}
            type="number"
            min={8}
            max={64}
            value={length}
            onChange={(e) => {
              setLength(e.target.value)
              regenerate(e.target.value, charsets)
            }}
            className="h-8 w-16 rounded-md border border-input bg-transparent px-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </label>
        {CHARSETS.map(([key, label]) => (
          <label key={key} className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={charsets[key]}
              onChange={(e) => {
                const next = { ...charsets, [key]: e.target.checked }
                setCharsets(next)
                regenerate(length, next)
              }}
            />
            {label}
          </label>
        ))}
      </div>
      {problem && <Alert>{PROBLEM_MESSAGES[problem]}</Alert>}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" size="sm" onClick={onClose}>
          닫기
        </Button>
        <Button variant="outline" size="sm" icon={<RefreshCw aria-hidden className="size-4" />} onClick={() => regenerate(length, charsets)}>
          다시 생성
        </Button>
        <Button size="sm" onClick={() => onUse(password)} disabled={problem !== null}>
          이 비밀번호 사용
        </Button>
      </div>
    </div>
  )
}
