package com.keymanage.android.domain.vault

import java.text.Collator
import java.util.Locale

/** 화면에 보일 항목 고르기(VLT-001). 웹 `listItems`와 같은 규칙이다. */
object ItemList {
    private val collator: Collator = Collator.getInstance(Locale.KOREAN).apply { strength = Collator.PRIMARY }

    /**
     * 삭제되지 않은 항목을 서비스 이름순으로 돌려준다. [query]는 서비스 이름·아이디·URL에서 대소문자 없이 찾는다.
     * 비밀번호·메모는 검색하지 않는다. 화면에 보이지 않는 값으로 항목이 걸리면 혼란스럽다.
     */
    fun list(vault: VaultData, query: String): List<VaultItem> {
        val needle = query.trim().lowercase()
        return vault.visibleItems
            .filter { item -> needle.isEmpty() || listOf(item.serviceName, item.username, item.url).any { it?.lowercase()?.contains(needle) == true } }
            .sortedWith { a, b -> collator.compare(a.serviceName.orEmpty(), b.serviceName.orEmpty()).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
    }
}
