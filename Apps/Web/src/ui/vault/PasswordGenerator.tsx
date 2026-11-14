// 비밀번호 생성기 (GEN-001). 만든 비밀번호는 사용을 누르기 전까지 어디에도 저장하지 않는다
import { Copy, RefreshCw } from 'lucide-react'
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

// 숫자만 받는다. Number('1e1')처럼 칸에 보이는 값과 다른 길이로 만들지 않게 한다
const parseLength = (text: string) => (/^\d+$/.test(text.trim()) ? Number(text) : Number.NaN)

export interface PasswordGeneratorProps {
  onUse?: (password: string) => void
  onClose?: () => void
  onCopy?: (password: string) => void
  /** 테스트에서 결과를 고정할 때만 바꾼다 */
  generate?: (options: GeneratorOptions) => string
}

export function PasswordGenerator({ onUse, onClose, onCopy, generate = generatePassword }: PasswordGeneratorProps) {
  // 입력 중에 비울 수 있도록 길이는 글자로 들고 있는다
  const [length, setLength] = useState(String(DEFAULT_GENERATOR_OPTIONS.length))
  const [charsets, setCharsets] = useState<Charsets>(DEFAULT_GENERATOR_OPTIONS)
  const [password, setPassword] = useState(() => generate(DEFAULT_GENERATOR_OPTIONS))
  // 길이 경고는 입력을 마친 뒤(칸을 벗어나거나 다시 생성)에만 보인다. 16을 20으로 고치는 중에 깜빡이지 않게 한다
  const [lengthChecked, setLengthChecked] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const lengthId = useId()

  const problem = checkGeneratorOptions({ ...charsets, length: parseLength(length) })
  const shownProblem = problem === 'noCharset' || (problem === 'length' && lengthChecked) ? problem : null

  function regenerate(nextLength: string, nextCharsets: Charsets) {
    const options = { ...nextCharsets, length: parseLength(nextLength) }
    if (checkGeneratorOptions(options) !== null) return
    setLengthChecked(false)
    setPassword(generate(options))
    // 원문을 읽어 주지 않고 바뀌었다는 사실만 알린다
    setAnnouncement('새 비밀번호를 만들었습니다.')
  }

  // 바깥 항목 폼은 Enter를 저장으로 처리한다. 생성기 안의 Enter는 저장으로 넘기지 않는다
  function stopEnter(event: KeyboardEvent) {
    if (event.key === 'Enter') event.stopPropagation()
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/40 p-3" onKeyDown={stopEnter}>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {/* 옵션이 잘못된 동안에는 옵션과 맞지 않는 이전 결과를 보이지 않는다 */}
      {problem === null && (
        <>
          <p data-testid="generated-password" className="break-all font-mono text-label">
            {password}
          </p>
          <EntropyGauge score={scorePassword(password)} label="생성된 비밀번호 강도" />
        </>
      )}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-label">
        <label htmlFor={lengthId} className="flex items-center gap-2">
          길이
          <input
            id={lengthId}
            type="number"
            min={8}
            max={64}
            value={length}
            autoFocus
            onChange={(e) => {
              setLength(e.target.value)
              regenerate(e.target.value, charsets)
            }}
            onBlur={() => setLengthChecked(true)}
            className="h-8 w-16 rounded-md border border-input bg-transparent px-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </label>
        {CHARSETS.map(([key, label]) => (
          <label key={key} className="flex items-center gap-2">
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
      {shownProblem && <Alert>{PROBLEM_MESSAGES[shownProblem]}</Alert>}
      <div className="flex flex-wrap justify-end gap-2">
        {onClose && (
          <Button variant="ghost" size="sm" onClick={onClose}>
            닫기
          </Button>
        )}
        <Button
          variant="outline"
          size="sm"
          icon={<RefreshCw aria-hidden className="size-4" />}
          onClick={() => {
            setLengthChecked(true)
            regenerate(length, charsets)
          }}
        >
          다시 생성
        </Button>
        {onCopy && (
          <Button
            variant="outline"
            size="sm"
            icon={<Copy aria-hidden className="size-4" />}
            onClick={() => onCopy(password)}
            disabled={problem !== null}
          >
            복사
          </Button>
        )}
        {onUse && (
          <Button size="sm" onClick={() => onUse(password)} disabled={problem !== null}>
            이 비밀번호 사용
          </Button>
        )}
      </div>
    </div>
  )
}
