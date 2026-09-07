package com.pawtrail.user.domain.provider.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 다른 서비스에서 받아온 장소 하나입니다.
 *
 * 카드에 필요한 값만 담습니다.
 * 즐겨찾기와 방문 기록과 일정이 같은 카드 컴포넌트를 쓰므로 셋이 이 타입을 함께 씁니다.
 *
 * 주소는 담지 않습니다.
 * 세 화면 어디에도 주소가 없고 명세 응답에도 없습니다.
 *
 * @param placeId     장소 식별자입니다.
 * @param name        장소 이름입니다. 이 값이 없으면 카드가 성립하지 않습니다.
 * @param placeType   카테고리 9종입니다. 카테고리 칩과 지도 마커 색이 이 값을 씁니다.
 * @param imageUrl    대표 사진입니다. 없을 수 있습니다.
 * @param lat         위도입니다. 일정 화면의 지도 마커와 경로선이 씁니다.
 * @param lon         경도입니다.
 * @param supplyPoint 편의점이나 마트처럼 동선 중 보급 지점인지입니다.
 *                    지도 범례가 반려견 간식·용품점을 따로 두는데,
 *                    placeType 9종에 그 값이 없어 ETC 로 들어오므로 이 플래그로 가릅니다.
 */
public record PlaceData(
        UUID placeId,
        String name,
        String placeType,
        String imageUrl,
        BigDecimal lat,
        BigDecimal lon,
        boolean supplyPoint) {
}
