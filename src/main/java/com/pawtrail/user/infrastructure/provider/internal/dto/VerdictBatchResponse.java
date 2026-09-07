package com.pawtrail.user.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * POST /internal/verdicts/batch 의 data 부분입니다.
 *
 * 봉투(CommonApiResponse)를 벗긴 안쪽만 담습니다.
 * 이 형태는 명세에 그대로 적혀 있는 것을 옮긴 것입니다.
 *
 * 목록용 응답이라 항목별 판정 이유(reasons)가 담기지 않습니다.
 * 부피가 커서 장소 상세에서만 내려갑니다.
 *
 * hasConflict 와 evidenceSummary 는 받기는 하지만 카드가 쓰지 않습니다.
 * 그쪽 규격을 그대로 옮긴 것이라 우리가 빼지 않습니다.
 * 도메인으로 넘어가는 VerdictData 에는 카드가 쓰는 둘만 담깁니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerdictBatchResponse(List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String placeId,
            Boolean hasConflict,
            List<PetVerdict> verdicts,
            String evidenceSummary,
            List<String> requiredItems) {
    }

    /**
     * 마리별 판정입니다.
     *
     * 검색은 "모두 함께" 를 지원해 여러 마리가 담기지만
     * 즐겨찾기는 대표 한 마리만 넘기므로 원소가 0개나 1개입니다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PetVerdict(String petId, String verdict) {
    }
}
