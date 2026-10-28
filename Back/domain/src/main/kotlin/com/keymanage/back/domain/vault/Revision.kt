package com.keymanage.back.domain.vault

/** 서버가 발급하는 볼트 revision. 저장에 성공할 때마다 1씩 오른다 (VLT-001, ADR-0006) */
@JvmInline
value class Revision(val value: Long) {

    init {
        require(value >= 0) { "revision은 0 이상이어야 함: $value" }
    }

    fun next(): Revision = Revision(value + 1)

    companion object {
        /** 볼트가 아직 없을 때의 revision. 첫 저장 요청은 이 값을 기대한다 */
        val NONE = Revision(0)
    }
}
