package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.VisitLog;
import java.util.Optional;
import java.util.UUID;

/**
 * 방문 기록을 저장하고 찾아오는 약속입니다.
 *
 * 지금은 최소한만 둡니다.
 * 목록 조회와 일정 연쇄 삭제는 그 API 를 만드는 이슈에서 더합니다.
 */
public interface VisitLogRepository {

    VisitLog save(VisitLog visitLog);

    Optional<VisitLog> findById(UUID id);

    void delete(VisitLog visitLog);
}
