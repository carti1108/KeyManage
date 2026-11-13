package com.keymanage.feature.vaultlist

import com.carti.ribs.Dependency
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
import kotlin.time.Clock

interface VaultListDependency : Dependency {
    val vaultListViewFactory: VaultListViewFactory
    val session: SessionPort
    val clipboard: ClipboardPort
    val clock: Clock
    /** 폼 안의 비밀번호 생성기(자식 RIB)가 쓴다 */
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val randomBytes: RandomBytes
}

/** 부모에게 올리는 이벤트. 잠김·로그아웃은 세션이 알리고 부모(Main)가 처리한다 */
interface VaultListListener {
    /** 저장(추가·수정·삭제)이 성공했다. 부모가 지난 동기화 오류를 걷는다 (웹 SessionRoot의 onSave) */
    fun vaultListDidSave()
}
