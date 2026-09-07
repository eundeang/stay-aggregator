package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.Stay
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierFailureReason
import io.swagger.v3.oas.annotations.media.Schema

/** 검색 응답 최상위 구조. 근거: docs/domain-model.md "응답 구조". */
@Schema(description = "숙박 상품 검색 응답")
data class StaySearchResult(
    @Schema(description = "검색된 숙박 상품 목록")
    val results: List<Stay>,
    @Schema(description = "일부 공급사 조회 실패 목록 — 비어있으면 전체 공급사 정상 응답")
    val partialFailures: List<PartialFailure>,
)

/**
 * 공급사 하나(청크 하나)의 실패 1건. 같은 공급사가 여러 청크로 나뉘어 호출된
 * 경우, 청크마다 실패하면 같은 supplier 값을 가진 항목이 여러 개 쌓일 수 있다
 * — 스펙에 이 경우가 명시돼 있지 않아, 실패를 숨기지 않고 전부 노출하는
 * 쪽을 택함(근거: JOURNAL.md).
 */
@Schema(description = "공급사 하나의 조회 실패 1건")
data class PartialFailure(
    @Schema(description = "조회에 실패한 공급사")
    val supplier: SupplierCode,
    @Schema(description = "실패 사유")
    val reason: SupplierFailureReason,
)
