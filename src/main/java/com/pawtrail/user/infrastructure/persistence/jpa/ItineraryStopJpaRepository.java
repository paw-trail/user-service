package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.ItineraryStop;
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
public interface ItineraryStopJpaRepository extends JpaRepository<ItineraryStop, UUID> {

    Optional<ItineraryStop> findByIdAndAccountId(UUID id, UUID accountId);

    Optional<ItineraryStop> findByAccountIdAndPlaceIdAndVisitAt(UUID accountId,
                                                                UUID placeId,
                                                                LocalDateTime visitAt);

    /**
     * 그날의 일정을 시각 순으로, 시각이 같으면 순서대로 돌려줍니다.
     *
     * 파생 쿼리로 쓰면 메서드 이름이 조건 넷과 정렬 둘을 전부 담아 읽기 어려워집니다.
     *
     * 끝 경계를 포함하지 않습니다.
     * BETWEEN 은 양끝을 포함해 다음 날 00:00 짜리 일정이 함께 걸립니다.
     */
    @Query("""
            select s from ItineraryStop s
            where s.accountId = :accountId
              and s.visitAt >= :dayStart
              and s.visitAt < :dayEnd
            order by s.visitAt asc, s.visitOrder asc
            """)
    List<ItineraryStop> findAllByAccountIdAndDay(@Param("accountId") UUID accountId,
                                                 @Param("dayStart") LocalDateTime dayStart,
                                                 @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * 그날의 마지막 순서를 돌려줍니다.
     *
     * 그날에 담긴 일정이 하나도 없으면 max 가 null 이므로 0 으로 바꿔 돌려줍니다.
     * 부르는 쪽이 1 을 더하면 첫 순서가 1 이 됩니다.
     *
     * 집계라 행을 읽어오지 않고 인덱스만 훑습니다.
     */
    @Query("""
            select coalesce(max(s.visitOrder), 0) from ItineraryStop s
            where s.accountId = :accountId
              and s.visitAt >= :dayStart
              and s.visitAt < :dayEnd
            """)
    int findMaxVisitOrder(@Param("accountId") UUID accountId,
                          @Param("dayStart") LocalDateTime dayStart,
                          @Param("dayEnd") LocalDateTime dayEnd);

    /**
     * 그 범위에 담긴 일정의 방문 예정 일시를 이른 것부터 돌려줍니다.
     *
     * 날짜로 접는 일은 서비스가 합니다.
     * 조회 대상 자리에서 날짜를 잘라내면 반환 타입 매핑이 확실하지 않습니다.
     *
     * 이른 것부터 돌려주므로 서비스가 중복을 접어도 순서가 유지됩니다.
     */
    @Query("""
            select s.visitAt from ItineraryStop s
            where s.accountId = :accountId
              and s.visitAt >= :from
              and s.visitAt < :toExclusive
            order by s.visitAt asc
            """)
    List<LocalDateTime> findVisitAtsInRange(@Param("accountId") UUID accountId,
                                            @Param("from") LocalDateTime from,
                                            @Param("toExclusive") LocalDateTime toExclusive);
}
