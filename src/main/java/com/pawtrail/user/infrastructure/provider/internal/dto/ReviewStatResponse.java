package com.pawtrail.user.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /internal/reviews/stats?placeIds= 의 data 안에 담기는 원소입니다.
 *
 * 봉투(CommonApiResponse)를 벗긴 안쪽만 담습니다.
 *
 * 이 응답 형태도 명세에 없습니다.
 * 명세에는 검색이 평점을 하루 한 번 동기화할 때 부른다고만 적혀 있고
 * 무엇이 오는지가 없어, 부르는 쪽인 우리가 정했습니다.
 * review 서비스를 만들 때 이 형태에 맞춰야 합니다.
 *
 * ratingAvg 가 null 일 수 있습니다.
 * 후기가 하나도 없는 장소는 평균을 낼 것이 없어 0 이 아니라 없는 값입니다.
 *
 * reviewCount 는 받기는 하지만 즐겨찾기 카드가 쓰지 않습니다.
 * 검색이 쓰는 값이라 그쪽 규격에 함께 있는 것입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewStatResponse(String placeId, Double ratingAvg, Integer reviewCount) {
}
