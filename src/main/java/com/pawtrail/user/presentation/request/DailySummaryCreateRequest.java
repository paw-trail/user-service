package com.pawtrail.user.presentation.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 하루 요약 생성 요청입니다.
 *
 * 누구의 요약인지는 받지 않습니다.
 * 게이트웨이가 넣은 X-User-Id 를 컨트롤러가 꺼내 씁니다.
 *
 * 이 서비스에서 날짜만 받는 유일한 자리입니다.
 * 다른 곳은 시각을 함께 담는데, 요약은 하루를 통째로 묶는 것이라 시각이 뜻을 갖지 않습니다.
 * 저장할 때 서버가 그 날짜의 00:00 을 채웁니다.
 *
 * 계정과 날짜가 함께 기본 키라 하루에 한 줄입니다.
 * 시각이 섞이면 그 키가 하루에 한 줄을 못 막아 같은 날 요약이 여러 줄 쌓입니다.
 *
 * toInput 을 두지 않았습니다.
 * 넘길 값이 날짜 하나뿐이라 record 를 하나 더 만들 이유가 없습니다.
 *
 * @param visitDate 요약할 날짜입니다.
 */
public record DailySummaryCreateRequest(

        @NotNull(message = "요약할 날짜는 필수입니다")
        LocalDate visitDate
) {
}
