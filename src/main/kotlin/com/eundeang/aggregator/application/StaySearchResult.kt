package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.Stay
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierFailureReason

/** 검색 응답 최상위 구조. 근거: docs/domain-model.md "응답 구조". */
data class StaySearchResult(
    val results: List<Stay>,
    val partialFailures: List<PartialFailure>,
)

/**
 * 공급사 하나(청크 하나)의 실패 1건. 같은 공급사가 여러 청크로 나뉘어 호출된
 * 경우, 청크마다 실패하면 같은 supplier 값을 가진 항목이 여러 개 쌓일 수 있다
 * — 스펙에 이 경우가 명시돼 있지 않아, 실패를 숨기지 않고 전부 노출하는
 * 쪽을 택함(근거: JOURNAL.md).
 */
data class PartialFailure(
    val supplier: SupplierCode,
    val reason: SupplierFailureReason,
)
