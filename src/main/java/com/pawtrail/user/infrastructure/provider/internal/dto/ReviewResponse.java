package com.pawtrail.user.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /internal/reviews?accountId=&from=&to= 의 data 안에 담기는 원소입니다.
 *
 * 봉투(CommonApiResponse)를 벗긴 안쪽만 담습니다.
 *
 * 이 응답 형태도 명세에 없습니다.
 * 명세에는 user 가 하루 요약의 재료로 부른다고만 적혀 있고 무엇이 오는지가 없어,
 * 부르는 쪽인 우리가 여섯 필드로 정했습니다.
 * review 서비스를 만들 때 이 형태에 맞춰야 합니다.
 *
 * 식별자와 날짜를 문자열로 받습니다.
 * 그쪽이 문자열로 보내기 때문이며 UUID 와 LocalDate 로 바꾸는 일은 구현이 합니다.
 *
 * 페이징하지 않습니다.
 * 한 사람의 특정 기간 후기라 하루면 많아야 몇 건입니다.
 * 즐겨찾기의 GET /internal/favorites?placeId= 에 페이징을 붙인 것은
 * 한 장소에 몰린 사람 수라 성격이 반대였습니다.
 *
 * 견종은 목록으로 받습니다.
 * 후기 한 건에 반려동물을 여러 마리 담을 수 있어 한 칸으로는 담기지 않습니다.
 * 견종을 정하지 않은 아이는 review 가 걸러 보내므로 빈 목록이 올 수 있습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewResponse(String reviewId,
                             String placeId,
                             String visitedAt,
                             Integer rating,
                             String content,
                             List<String> petBreedsAtVisit) {
}
