package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.VisitLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface VisitLogJpaRepository extends JpaRepository<VisitLog, UUID> {

    long countByAccountId(UUID accountId);

    Optional<VisitLog> findByIdAndAccountId(UUID id, UUID accountId);

    Optional<VisitLog> findByItineraryStopId(UUID itineraryStopId);

    /**
     * 그 사람의 방문 기록을 날짜 최신순 · 하루 안 시간순으로 돌려줍니다.
     *
     * 파생 쿼리로는 표현할 수 없어 JPQL 을 씁니다.
     * 같은 컬럼을 두 번, 그것도 반대 방향으로 정렬해야 하기 때문입니다.
     *
     * function('date', ...) 로 데이터베이스의 date 함수를 부릅니다.
     * JPQL 표준에는 timestamp 에서 날짜만 잘라내는 함수가 없습니다.
     *
     * 이 정렬은 idx_visit_log_account 를 절반만 씁니다.
     * account_id 로 좁히는 데까지는 인덱스가 쓰이고 정렬은 따로 한 번 돕니다.
     * 한 사람의 방문 기록이라 건수가 작아 그대로 둡니다.
     */
    @Query("""
            select v from VisitLog v
            where v.accountId = :accountId
            order by function('date', v.visitedAt) desc, v.visitedAt asc
            """)
    List<VisitLog> findAllByAccountIdOrderByVisitedAt(@Param("accountId") UUID accountId);

    /**
     * 그 사람의 그날 방문 기록을 이른 것부터 돌려줍니다.
     *
     * 파생 쿼리로 쓰면 메서드 이름이 조건 셋과 정렬을 전부 담아 읽기 어려워집니다.
     *
     * 끝 경계를 포함하지 않습니다.
     * BETWEEN 은 양끝을 포함해 다음 날 00:00 짜리가 함께 걸립니다.
     */
    @Query("""
            select v from VisitLog v
            where v.accountId = :accountId
              and v.visitedAt >= :dayStart
              and v.visitedAt < :dayEnd
            order by v.visitedAt asc
            """)
    List<VisitLog> findAllByAccountIdAndDay(@Param("accountId") UUID accountId,
                                            @Param("dayStart") LocalDateTime dayStart,
                                            @Param("dayEnd") LocalDateTime dayEnd);
}
