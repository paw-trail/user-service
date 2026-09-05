package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.VisitLog;
import java.util.Optional;
import java.util.UUID;

/**
 * 방문 기록을 저장하고 찾아오는 약속입니다.
 */
public interface VisitLogRepository {

    VisitLog save(VisitLog visitLog);

    Optional<VisitLog> findById(UUID id);

    void delete(VisitLog visitLog);

    // 그 사람의 방문 기록 수를 셈
    // 마이페이지 상단의 stats.visitCount 가 이 값임
    //
    // "갔다고 사용자가 확인한 것" 만 세짐
    // 담아두고 안 간 곳은 itinerary_stop 에만 있어 여기 안 걸림
    long countByAccountId(UUID accountId);
}
