// 서버 응답도 믿지 않는다. 필요한 필드의 타입만 확인해 꺼내고, 값의 규격 검사는 domain이 한다
import { UnexpectedResponseError } from '../../domain/server/serverErrors'

export function asRecord(body: unknown): Record<string, unknown> {
  if (typeof body !== 'object' || body === null) throw new UnexpectedResponseError('응답 본문이 객체가 아님')
  return body as Record<string, unknown>
}
