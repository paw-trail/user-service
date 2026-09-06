package com.pawtrail.user.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /internal/reviews/count?accountId= 의 data 부분입니다.
 *
 * 봉투(CommonApiResponse)를 벗긴 안쪽만 담습니다.
 * 벗기는 일은 이 레코드가 아니라 ReviewProviderImpl 이 합니다.
 *
 * infrastructure 아래에 두는 것은 이것이 그쪽 서비스의 형태이기 때문입니다.
 * 도메인이 쓰는 값은 count 하나뿐이라 이 형태를 위로 올리지 않습니다.
 *
 * 모르는 필드는 무시합니다.
 * review 가 나중에 필드를 더해도 우리 쪽이 깨지지 않습니다.
 * 그쪽 규격이라 우리가 정하는 것이 아닙니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewCountResponse(Long count) {
}
