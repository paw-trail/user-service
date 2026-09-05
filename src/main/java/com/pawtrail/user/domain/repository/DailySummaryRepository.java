package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.DailySummaryId;
import java.util.Optional;

/**
 * 하루 요약을 저장하고 찾아오는 약속입니다.
 *
 * 식별자가 복합 키라 UUID 하나가 아니라 DailySummaryId 를 받습니다.
 * 부르는 쪽은 계정 식별자와 00:00 으로 자른 날짜로 그 값을 만듭니다.
 *
 * 지금은 최소한만 둡니다.
 * 방문 목록에 붙일 날짜 구간 조회는 그 API 를 만드는 이슈에서 더합니다.
 */
public interface DailySummaryRepository {

    DailySummary save(DailySummary dailySummary);

    Optional<DailySummary> findById(DailySummaryId id);
}
