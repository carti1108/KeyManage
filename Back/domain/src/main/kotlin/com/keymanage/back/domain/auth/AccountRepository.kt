package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId

/** 계정 저장소 포트. 구현체는 infrastructure 모듈에 둔다 */
interface AccountRepository {

    fun find(email: Email): Account?

    /**
     * 같은 이메일의 계정이 없을 때만 만든다. 확인과 생성은 원자적이어야 한다: 동시 가입 중 하나만 성공한다 (SEC-002)
     *
     * @return 만들었으면 true, 이미 있었으면 false
     */
    fun createIfAbsent(account: Account): Boolean

    /**
     * 이 이메일의 계정 잠금을 잡고 [action]을 실행한다. [createIfAbsent]·[replaceIfMatches]와 같은 잠금이며 같은 스레드에서 다시 잡을 수 있다.
     * 비밀번호 변경이 계정 -> 볼트 순서로 잠금을 잡을 때 쓴다 (ADR-BE-001 결정 3)
     */
    fun <T> withLock(email: Email, action: () -> T): T

    /**
     * 저장된 계정이 [expected]와 같을 때만 [replacement]로 교체한다. 비교와 교체는 원자적이어야 하고,
     * 교체는 읽는 쪽에 옛 계정이나 새 계정 중 하나로만 보여야 한다 (ADR-BE-001 결정 2)
     *
     * @return 교체했으면 true, 계정이 없거나 달라서 교체하지 않았으면 false
     * @throws IllegalArgumentException 두 계정의 이메일이 다를 때
     */
    fun replaceIfMatches(expected: Account, replacement: Account): Boolean

    /**
     * 저장된 계정이 [expected]와 같을 때만 지운다. 비교와 삭제는 원자적이어야 하고 [withLock]과 같은 잠금을 쓴다.
     * 지운 뒤에는 같은 이메일로 다시 만들 수 있다. 계정 삭제가 삭제를 확정할 때 쓴다 (SEC-005, ADR-0024)
     *
     * @return 지웠으면 true, 계정이 없거나 달라서 지우지 않았으면 false
     */
    fun deleteIfMatches(expected: Account): Boolean

    /**
     * 모든 계정이 가리키는 볼트 ID. 고아 볼트 정리가 쓴다 (ADR-BE-001 결정 5)
     *
     * @throws Exception 읽지 못한 계정이 있을 때. 그 계정의 볼트를 고아로 잘못 보지 않도록 일부만 돌려주지 않는다
     */
    fun allVaultIds(): Set<VaultId>
}
