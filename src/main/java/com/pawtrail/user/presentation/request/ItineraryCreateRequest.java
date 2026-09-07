package com.pawtrail.user.presentation.request;

import com.pawtrail.user.application.dto.input.ItineraryCreateInput;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 장소를 일정에 담는 요청입니다.
 *
 * 누가 담는지는 받지 않습니다.
 * 게이트웨이가 넣은 X-User-Id 를 컨트롤러가 @CurrentUser 로 꺼내 씁니다.
 *
 * visitOrder 를 받지 않습니다.
 * 서버가 그날 마지막 순서를 조회해 채웁니다.
 * 요청으로 받으면 사용자가 남의 순서와 겹치는 값을 넣을 수 있고,
 * 애초에 화면에 그 값을 입력할 자리도 없습니다.
 *
 * visitAt 은 날짜와 시각을 함께 담습니다.
 * 화면은 날짜 드롭다운과 시각 입력을 따로 두지만 프론트가 하나로 조립해 보냅니다.
 * 시각을 정하지 않았으면 그 날짜의 00:00 을 보냅니다.
 *
 * @param placeId 담을 장소입니다.
 * @param visitAt 방문 예정 일시입니다.
 * @param petId   동반 예정 동물입니다. 없으면 보내지 않습니다.
 * @param memo    일정 메모입니다.
 */
public record ItineraryCreateRequest(

        @NotNull(message = "장소를 선택해 주세요")
        UUID placeId,

        @NotNull(message = "방문 예정 일시는 필수입니다")
        LocalDateTime visitAt,

        UUID petId,

        // 컬럼이 varchar(200) 이라 길이를 맞춤
        // 여기서 안 막으면 DB 가 막는데, 그때는 어느 필드가 문제인지 응답에 안 실림
        @Size(max = 200, message = "메모는 200자까지 쓸 수 있습니다")
        String memo
) {

    public ItineraryCreateInput toInput() {
        return new ItineraryCreateInput(placeId, visitAt, petId, memo);
    }
}
