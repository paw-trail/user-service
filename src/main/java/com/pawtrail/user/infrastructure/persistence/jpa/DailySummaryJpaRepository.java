package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.DailySummaryId;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 *
 * 식별자 타입이 UUID 가 아니라 DailySummaryId 입니다.
 * 이 표만 기본 키가 둘이기 때문입니다.
 */
public interface DailySummaryJpaRepository extends JpaRepository<DailySummary, DailySummaryId> {

    List<DailySummary> findByAccountIdAndVisitDateIn(UUID accountId, Collection<LocalDateTime> visitDates);
    /**
     * 그 계정의 하루 요약 을 한 번에 지웁니다.
     *
     * 탈퇴 처리가 씁니다.
     *
     * 파생 쿼리를 쓰지 않는 이유는 그 방식이 엔티티를 전부 읽어 온 뒤 하나씩 지우기 때문입니다.
     * 이벤트 소비 경로라 트랜잭션이 길어지면 재시도와 DLQ 판단에 그대로 걸립니다.
     * 반환값인 지운 행 수도 필요합니다. 이 경로는 응답이 없어 로그가 유일한 흔적입니다.
     *
     * 기본 키가 둘이지만 계정 식별자 하나를 조건으로 씁니다.
     * 복합 키의 한쪽만 보는 것이라 날짜와 무관하게 그 계정의 행이 전부 지워집니다.
     *
     * flushAutomatically 를 켭니다.
     * 이 쿼리가 건드리는 표는 daily_summary 인데 부르기 직전에 고친 것은 user_profile 입니다.
     * 하이버네이트는 쿼리가 건드리는 표를 보고 반영 여부를 정하므로
     * 프로필의 변경이 아직 반영되지 않은 채로 이 쿼리가 나갈 수 있습니다.
     *
     * clearAutomatically 는 켜지 않습니다.
     * 켜면 영속성 컨텍스트가 통째로 비워져 방금 익명화한 프로필이 준영속이 됩니다.
     * 그 변경이 오류 없이 사라지므로 "지웠다고 로그는 찍혔는데 닉네임은 그대로" 가 됩니다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from DailySummary d where d.accountId = :accountId")
    int deleteAllByAccountId(@Param("accountId") UUID accountId);
}
