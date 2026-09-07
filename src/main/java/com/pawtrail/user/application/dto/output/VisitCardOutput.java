package com.pawtrail.user.application.dto.output;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 방문 기록 목록의 카드 한 장입니다.
 *
 * 값이 다섯 곳에서 옵니다.
 *
 *   visit_log       visitId · placeId · visitedAt · petId · verdictAtVisit · memo
 *   place           name · placeType · imageUrl
 *   verdict         requiredItems
 *   review          ratingAvg
 *   favorite        isFavorite
 *   daily_summary   summary
 *
 * 즐겨찾기와 같은 카드 컴포넌트를 쓰지만 record 는 따로 둡니다.
 * 같은 이름의 필드가 서로 다른 것을 뜻하기 때문입니다.
 *
 *   즐겨찾기   verdict          지금 판정.  조건이 바뀌면 배지도 바뀜
 *   방문 기록   verdictAtVisit   기록한 시점의 판정.  안 바뀜
 *
 * @param visitId        기록 식별자입니다. 삭제할 때 프론트가 이 값을 보냅니다.
 * @param placeId        장소 식별자입니다. 하트를 누를 때 이 값을 씁니다.
 * @param name           장소 이름입니다. place 에서 옵니다.
 * @param placeType      카테고리입니다. 프론트가 이 값을 세어 칩을 만듭니다.
 * @param imageUrl       대표 사진입니다. 없을 수 있습니다.
 * @param requiredItems  준비물입니다. 비어 있을 수 있고 null 은 되지 않습니다.
 *                       판정 배지와 달리 저장하지 않고 볼 때마다 verdict 를 불러 채웁니다.
 *                       "챙길 것" 이라는 안내라 지금 값이 맞습니다.
 * @param ratingAvg      평점 평균입니다. 후기가 없거나 못 받아오면 null 입니다.
 * @param visitedAt      다녀온 일시입니다. 일정에서 온 방문이면 그 일정의 visitAt 입니다.
 * @param petId          함께 간 반려동물입니다. 펫이 0마리면 null 입니다.
 * @param verdictAtVisit 기록한 시점의 판정입니다. 조건이 바뀌어도 안 바뀝니다.
 * @param memo           방문 메모입니다.
 * @param summary        그날 AI 요약입니다. 그날 첫 행에만 실리고 나머지는 null 입니다.
 * @param isFavorite     하트를 채울지 판단하는 값입니다. 이 화면은 하트로 추가와 해제를 다 합니다.
 */
public record VisitCardOutput(UUID visitId,
                              UUID placeId,
                              String name,
                              String placeType,
                              String imageUrl,
                              List<String> requiredItems,
                              Double ratingAvg,
                              LocalDateTime visitedAt,
                              UUID petId,
                              String verdictAtVisit,
                              String memo,
                              String summary,
                              boolean isFavorite) {
}
