package com.pawtrail.user.application.dto.output;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 하루 요약을 만든 결과입니다.
 *
 * 명세가 정한 세 필드를 그대로 담습니다.
 *
 * visitDate 를 돌려주는 것은 요청한 날짜를 그대로 되비추기 위해서입니다.
 * 프론트가 여러 날의 카드를 열어 두고 각각 버튼을 누를 수 있어,
 * 응답이 어느 카드의 것인지 알아야 그 자리에 문장을 그릴 수 있습니다.
 *
 * generatedAt 은 목록 조회의 값과 같은 뜻입니다.
 * 만든 시각이며 요약 대상 날짜와는 다릅니다.
 *
 * @param visitDate   요약한 날짜입니다.
 * @param summary     만들어진 문장입니다.
 * @param generatedAt 만든 시각입니다.
 */
public record DailySummaryOutput(LocalDate visitDate,
                                 String summary,
                                 LocalDateTime generatedAt) {
}
