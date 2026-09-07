package com.pawtrail.user.application.dto.output;

import java.util.UUID;

/**
 * 장소를 일정에 담은 결과입니다.
 *
 * 식별자 하나만 돌려줍니다.
 * 서버가 만든 값이라 프론트가 알 방법이 없고,
 * 받아 두면 목록을 다시 부르지 않아도 그 자리에서 삭제 버튼을 그릴 수 있습니다.
 *
 * 이미 같은 시각에 같은 장소를 담아 두었다면 그 행의 식별자가 나갑니다.
 * 새로 만들지 않으므로 201 이 아니라 200 입니다.
 */
public record ItineraryCreateOutput(UUID stopId) {
}
