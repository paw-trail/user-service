package com.pawtrail.user.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * GET /internal/places?ids= 의 data 안에 담기는 원소입니다.
 *
 * 봉투(CommonApiResponse)를 벗긴 안쪽만 담습니다.
 * 벗기는 일은 이 레코드가 아니라 PlaceProviderImpl 이 합니다.
 *
 * infrastructure 아래에 두는 것은 이것이 그쪽 서비스의 형태이기 때문입니다.
 * 도메인이 보는 값은 PlaceData 이고 구현이 이 타입을 그쪽으로 바꿉니다.
 *
 * 이 응답 형태는 명세에 없습니다.
 * 명세에는 누가 부르는지만 있고 무엇이 오는지가 적혀 있지 않아,
 * 부르는 쪽인 우리가 필요한 일곱 필드로 정했습니다.
 * place 서비스를 만들 때 이 형태에 맞춰야 합니다.
 *
 * placeId 를 String 으로 받는 것은 그쪽이 문자열로 보내기 때문입니다.
 * UUID 로 바꾸는 일은 구현이 합니다.
 *
 * supplyPoint 를 기본 타입으로 받습니다.
 * 그쪽 컬럼이 NOT NULL 이라 값이 언제나 오고, 빠져 있으면 false 가 됩니다.
 * 보급 지점이 아니라는 뜻이 되므로 안전한 쪽으로 떨어집니다.
 *
 * 모르는 필드는 무시합니다.
 * place 가 나중에 필드를 더해도 우리 쪽이 깨지지 않습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceResponse(
        String placeId,
        String name,
        String placeType,
        String imageUrl,
        BigDecimal lat,
        BigDecimal lon,
        boolean supplyPoint) {
}
