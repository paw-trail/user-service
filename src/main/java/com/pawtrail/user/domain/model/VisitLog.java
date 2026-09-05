package com.pawtrail.user.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.user.domain.enums.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 방문 기록입니다.
 *
 * 지난 날짜 일정 카드의 다녀왔어요 를 누르면 만들어집니다.
 * 날짜가 지났다고 자동으로 만들지 않습니다.
 * 담아두고 안 간 곳이 방문으로 기록되면
 * stats.visitCount 가 "가려고 계획한 곳 수" 가 되기 때문입니다.
 *
 * itinerary_stop 은 담은 것, 이 표는 갔다고 사용자가 확인한 것입니다.
 *
 * 하드 딜리트입니다.
 * Favorite 과 같은 이유이고, 일정을 지우면 연결된 이 표의 행도 함께 지우므로
 * 소프트 딜리트로 두면 없는 stopId 를 가리키는 행이 남습니다.
 */
@Entity
@Table(name = "visit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitLog extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    // 함께 간 반려동물임
    // 펫이 0마리면 null 임
    @Column(name = "pet_id", updatable = false)
    private UUID petId;

    // 다녀온 일시임
    //
    // 일정에서 온 방문이면 그 itinerary_stop.visit_at 을 그대로 복사함
    // 서버 시각을 찍으면 며칠 뒤에 눌렀을 때 값이 틀어짐
    // 9월 7일 일정을 9월 10일에 누르면 9월 10일이 되는데 그것은 틀린 값임
    @Column(name = "visited_at", nullable = false, updatable = false)
    private LocalDateTime visitedAt;

    // 판정 스냅샷임
    // 조건이 바뀌어도 이 값은 안 바뀜, 즐겨찾기 배지가 "지금" 인 것과 일부러 다르게 둠
    //
    // 펫이 0마리면 UNKNOWN 이 들어감
    // 다만 verdict 호출이 실패한 경우는 UNKNOWN 을 넣지 않고 요청 자체를 실패시킴
    // 나중에 고칠 방법이 없는 값이라 틀린 값을 남기지 않음
    //
    // 이름과 실제 값에 차이가 있음
    // 이름은 방문 시점의 판정을 뜻하지만 실제로는 기록한 시점의 판정이 들어감
    // verdict 가 무상태라 과거 시점 조건으로 판정할 방법이 없기 때문임
    // 최종 문서 정리 때 verdictAtRecord 로 바꿀 자리임
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict_at_visit", nullable = false, updatable = false, length = 16)
    private Verdict verdictAtVisit;

    // 어느 일정에서 온 방문인지임
    // 즉흥 방문이면 null 임
    //
    // uq_visit_log_itinerary_stop 이 걸려 있어 같은 카드에서 두 번 눌러도 한 번만 만들어짐
    // PostgreSQL 은 null 을 서로 다른 값으로 보므로 즉흥 방문끼리는 안 걸림
    @Column(name = "itinerary_stop_id", updatable = false)
    private UUID itineraryStopId;

    @Column(name = "memo", length = 200)
    private String memo;

    private VisitLog(UUID accountId, UUID placeId, UUID petId, LocalDateTime visitedAt,
                     Verdict verdictAtVisit, UUID itineraryStopId, String memo) {
        this.accountId = accountId;
        this.placeId = placeId;
        this.petId = petId;
        this.visitedAt = visitedAt;
        this.verdictAtVisit = verdictAtVisit;
        this.itineraryStopId = itineraryStopId;
        this.memo = memo;
    }

    /**
     * 방문을 기록합니다.
     *
     * itineraryStopId 가 있으면 place · visitedAt · pet 은 그 일정 행에서 읽은 값이어야 합니다.
     * 요청이 보낸 값을 그대로 믿으면 프론트가 잘못 조립하거나 요청을 조작했을 때
     * 일정과 방문 기록이 어긋납니다.
     * 소유권 검증을 하려면 어차피 그 행을 읽어야 하므로 값을 함께 꺼내는 비용은 없습니다.
     */
    public static VisitLog create(UUID accountId, UUID placeId, UUID petId,
                                  LocalDateTime visitedAt, Verdict verdictAtVisit,
                                  UUID itineraryStopId, String memo) {
        if (accountId == null || placeId == null) {
            throw new IllegalArgumentException("accountId 와 placeId 는 필수입니다.");
        }
        if (visitedAt == null) {
            throw new IllegalArgumentException("visitedAt 은 필수입니다.");
        }
        if (verdictAtVisit == null) {
            throw new IllegalArgumentException("verdictAtVisit 은 필수입니다.");
        }
        return new VisitLog(accountId, placeId, petId, visitedAt,
                verdictAtVisit, itineraryStopId, memo);
    }
}
