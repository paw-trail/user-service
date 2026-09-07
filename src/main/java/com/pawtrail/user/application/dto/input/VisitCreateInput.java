package com.pawtrail.user.application.dto.input;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 방문을 기록할 때 서비스가 받는 값입니다.
 *
 * 프론트가 보내는 요청 형태(VisitCreateRequest)와 나누어 둡니다.
 * 그쪽은 검증 애노테이션이 붙은 표현 계층의 것이고 이쪽은 검증을 통과한 뒤의 값입니다.
 *
 * itineraryStopId 가 있으면 placeId · visitedAt · petId 는 쓰이지 않습니다.
 * 서비스가 그 일정 행을 읽어 거기 값으로 채웁니다.
 * 그래도 셋을 받아 두는 것은 즉흥 방문 경로가 그 값을 쓰기 때문입니다.
 */
public record VisitCreateInput(UUID placeId,
                               UUID itineraryStopId,
                               LocalDateTime visitedAt,
                               UUID petId,
                               String memo) {
}
