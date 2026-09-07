package com.pawtrail.user.presentation.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 최근 본 장소 기록 요청입니다.
 *
 * 프론트가 장소 상세를 열거나 검색 결과에서 장소를 누를 때 자동으로 보냅니다.
 * 사용자가 누르는 버튼이 따로 있는 것이 아닙니다.
 *
 * 그 장소가 실제로 있는지는 여기서도 서비스에서도 보지 않습니다.
 * 담은 뒤에 사라지는 경우를 어차피 막을 수 없어 걸러내는 자리를 목록 조회 한 곳에 모았습니다.
 * 즐겨찾기와 일정도 같은 판단을 하고 있습니다.
 *
 * toInput 을 두지 않았습니다.
 * 넘길 값이 식별자 하나뿐이라 record 를 하나 더 만들 이유가 없습니다.
 *
 * @param placeId 방금 본 장소입니다.
 */
public record RecentPlaceCreateRequest(

        @NotNull(message = "장소는 필수입니다")
        UUID placeId
) {
}
