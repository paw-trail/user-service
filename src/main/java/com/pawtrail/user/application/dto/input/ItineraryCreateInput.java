package com.pawtrail.user.application.dto.input;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 장소를 일정에 담을 때 서비스가 받는 값입니다.
 *
 * visitOrder 가 없습니다.
 * 서버가 그날 마지막 순서를 조회해 채우므로 요청에서 받지 않습니다.
 *
 * 누가 담는지도 없습니다.
 * 게이트웨이가 넣은 X-User-Id 를 컨트롤러가 꺼내 따로 넘깁니다.
 *
 * @param placeId 담을 장소입니다.
 * @param visitAt 방문 예정 일시입니다. 시각을 안 정했으면 그 날짜의 00:00 입니다.
 * @param petId   동반 예정 동물입니다. 없으면 null 입니다.
 * @param memo    일정 메모입니다.
 */
public record ItineraryCreateInput(UUID placeId,
                                   LocalDateTime visitAt,
                                   UUID petId,
                                   String memo) {
}
