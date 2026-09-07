package com.pawtrail.user.presentation.request;

import com.pawtrail.user.application.dto.input.VisitCreateInput;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 방문 기록 요청입니다.
 *
 * 누가 기록하는지는 받지 않습니다.
 * 게이트웨이가 넣은 X-User-Id 를 컨트롤러가 @CurrentUser 로 꺼내 씁니다.
 *
 * itineraryStopId 가 있으면 placeId · visitedAt · petId 는 쓰이지 않습니다.
 * 서비스가 그 일정 행을 읽어 거기 값으로 채웁니다.
 * 요청이 보낸 값을 그대로 믿으면 일정과 방문 기록의 장소가 어긋날 수 있고,
 * 그러면 "어느 일정에서 왔는지" 라는 연결이 뜻을 잃습니다.
 *
 * 그래서 placeId 와 visitedAt 에 @NotNull 을 걸지 않습니다.
 * 명세는 둘을 필수로 두었지만 일정에서 온 방문은 보내지 않아도 됩니다.
 * 즉흥 방문일 때 없으면 서비스가 400 을 냅니다.
 *
 * @param placeId         다녀온 장소입니다. 즉흥 방문일 때만 쓰입니다.
 * @param itineraryStopId 어느 일정에서 왔는지입니다. 즉흥 방문이면 없습니다.
 * @param visitedAt       다녀온 일시입니다. 즉흥 방문일 때만 쓰입니다.
 * @param petId           함께 간 반려동물입니다. 없으면 판정이 UNKNOWN 으로 남습니다.
 * @param memo            방문 메모입니다.
 */
public record VisitCreateRequest(

        UUID placeId,

        UUID itineraryStopId,

        LocalDateTime visitedAt,

        UUID petId,

        // 컬럼이 varchar(200) 이라 길이를 맞춤
        // 여기서 안 막으면 DB 가 막는데, 그때는 어느 필드가 문제인지 응답에 안 실림
        @Size(max = 200, message = "메모는 200자까지 쓸 수 있습니다")
        String memo
) {

    public VisitCreateInput toInput() {
        return new VisitCreateInput(placeId, itineraryStopId, visitedAt, petId, memo);
    }
}
