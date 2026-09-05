package com.pawtrail.user.domain.enums;

/**
 * 동반 가능 여부 판정입니다.
 *
 * 값을 만드는 것은 verdict 서비스이고 user 는 받아서 저장하고 보여주기만 합니다.
 * 그래서 판정 규칙은 여기 없습니다.
 *
 * visit_log.verdict_at_visit 이 이 값을 문자열로 담습니다.
 * 즐겨찾기나 일정 카드의 배지는 저장하지 않고 볼 때마다 verdict 를 부릅니다.
 * 방문 기록만 저장하는 이유는 "그때 이랬다" 를 남기는 화면이기 때문입니다.
 */
public enum Verdict {

    // 조건 없이 동반할 수 있음
    ALLOWED,

    // 조건을 만족하면 동반할 수 있음
    CONDITIONAL,

    // 동반할 수 없음
    NOT_ALLOWED,

    // 판단할 조건 정보가 없음
    //
    // 반려동물이 0마리인 사용자가 방문을 기록하면 이 값이 들어감
    // 서버 장애로 판정을 못 받은 경우는 여기 해당하지 않음
    // 그때는 UNKNOWN 을 넣지 않고 요청 자체를 실패시킴, 두 상황이 섞이면 구분할 수 없음
    UNKNOWN
}
