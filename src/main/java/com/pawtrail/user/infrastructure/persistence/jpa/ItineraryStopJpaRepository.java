package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.ItineraryStop;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    /**
     * 그 계정의 일정 을 한 번에 지웁니다.
     *
     * 탈퇴 처리가 씁니다.
     *
     * 파생 쿼리를 쓰지 않는 이유는 그 방식이 엔티티를 전부 읽어 온 뒤 하나씩 지우기 때문입니다.
     * 이벤트 소비 경로라 트랜잭션이 길어지면 재시도와 DLQ 판단에 그대로 걸립니다.
     * 반환값인 지운 행 수도 필요합니다. 이 경로는 응답이 없어 로그가 유일한 흔적입니다.
     *
     * flushAutomatically 를 켭니다.
     * 이 쿼리가 건드리는 표는 itinerary_stop 인데 부르기 직전에 고친 것은 user_profile 입니다.
     * 하이버네이트는 쿼리가 건드리는 표를 보고 반영 여부를 정하므로
     * 프로필의 변경이 아직 반영되지 않은 채로 이 쿼리가 나갈 수 있습니다.
     *
     * clearAutomatically 는 켜지 않습니다.
     * 켜면 영속성 컨텍스트가 통째로 비워져 방금 익명화한 프로필이 준영속이 됩니다.
     * 그 변경이 오류 없이 사라지므로 "지웠다고 로그는 찍혔는데 닉네임은 그대로" 가 됩니다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ItineraryStop s where s.accountId = :accountId")
    int deleteAllByAccountId(@Param("accountId") UUID accountId);
}
