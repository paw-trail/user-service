package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.DailySummaryId;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 하루 요약을 저장하고 찾아오는 약속입니다.
 *
 * 식별자가 복합 키라 UUID 하나가 아니라 DailySummaryId 를 받습니다.
 * 부르는 쪽은 계정 식별자와 00:00 으로 자른 날짜로 그 값을 만듭니다.
 *
 */
public interface DailySummaryRepository {

    DailySummary save(DailySummary dailySummary);

    Optional<DailySummary> findById(DailySummaryId id);

    // 그 사람의 요약 중 주어진 날짜들의 것을 한 번에 찾음
    //
    // GET /api/v1/visits 가 씀
    // 방문 목록에 그날 요약을 붙이려면 목록에 나온 날짜들의 요약이 필요함
    //
    // 날짜마다 부르지 않는 이유
    // 방문한 날이 서른 날이면 조회가 서른 번이 됨
    // 즐겨찾기와 방문 기록의 장소 조회도 같은 이유로 한 번에 모아 부름
    //
    // 넘기는 날짜는 반드시 00:00 으로 잘라 둘 것
    // 이 표의 visit_date 는 timestamp 이지만 뜻은 날짜이고
    // 서버가 저장할 때 언제나 00:00 으로 고정함
    // 시각이 붙은 값으로 찾으면 하나도 안 걸림
    List<DailySummary> findByAccountIdAndVisitDateIn(UUID accountId, Collection<LocalDateTime> visitDates);
    // 그 계정의 하루 요약 을 한 번에 지움
    //
    // 탈퇴 처리가 씀
    // 반환은 지운 행 수임, 이벤트 소비는 응답이 없어 로그가 유일한 흔적이라 남겨 둠
    int deleteAllByAccountId(UUID accountId);
}
