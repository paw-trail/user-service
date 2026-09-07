package com.pawtrail.user.application.dto.output;

import java.util.UUID;

/**
 * 방문을 기록한 결과입니다.
 *
 * 식별자 하나만 돌려줍니다.
 * 프론트가 알 방법이 없는 값이고, 받아 두면 목록을 다시 부르지 않아도
 * 그 자리에서 삭제 버튼을 그릴 수 있습니다.
 *
 * 즐겨찾기 담기가 응답에 아무것도 담지 않은 것과 다른 점입니다.
 * 그쪽은 돌려줄 식별자(placeId)가 요청에 이미 있었습니다.
 *
 * 이미 기록한 일정을 다시 눌러도 이 값이 나갑니다.
 * 그때는 새로 만들지 않고 기존 기록의 식별자를 그대로 돌려줍니다.
 */
public record VisitCreateOutput(UUID visitId) {
}
