package com.pawtrail.user.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 날짜 단위 일정입니다.
 *
 * 제목이 있는 여행을 묶는 itinerary 표는 두지 않습니다.
 * title 을 입력받을 자리가 어느 화면에도 없었기 때문입니다.
 * 담은 장소 하나가 한 행이고, 날짜가 곧 일정 단위입니다.
 *
 * 하드 딜리트입니다.
 * 지우면 연결된 visit_log 도 함께 지웁니다.
 */
@Entity
@Table(name = "itinerary_stop")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryStop extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    // 방문 예정 일시임
    //
    // 시각을 안 정했으면 그 날짜의 00:00 이 들어감
    // 날짜로 고르는 조회는 BETWEEN 으로 함
    //
    // 예전에는 visit_date 와 planned_at 으로 나뉘어 있었음
    // 규약이 "시각은 전부 timestamp" 라 타입이 갈리면
    // 코드에서 LocalDate 와 LocalDateTime 을 오가며 변환이 낌
    @Column(name = "visit_at", nullable = false)
    private LocalDateTime visitAt;

    // 동반 예정 동물임
    // 펫이 0마리면 null 임
    @Column(name = "pet_id")
    private UUID petId;

    // 그날 안에서의 순서임
    // 서버가 "그날 마지막 + 1" 로 채우며 요청에서 받지 않음
    //
    // 사용자가 직접 바꾸는 기능은 두지 않음
    // 목록은 visit_at 순으로 정렬하고 이 값은 시각이 같을 때의 순서임
    // 시각을 안 정하면 visit_at 이 그날 00:00 이라 여럿이 같은 값이 되기 때문임
    //
    // 삭제해도 뒤의 값을 당기지 않음
    // 크기 비교로만 쓰이므로 1, 3, 4 여도 정렬은 정확함
    @Column(name = "visit_order", nullable = false)
    private Integer visitOrder;

    @Column(name = "memo", length = 200)
    private String memo;

    private ItineraryStop(UUID accountId, UUID placeId, LocalDateTime visitAt,
                          UUID petId, Integer visitOrder, String memo) {
        this.accountId = accountId;
        this.placeId = placeId;
        this.visitAt = visitAt;
        this.petId = petId;
        this.visitOrder = visitOrder;
        this.memo = memo;
    }

    /**
     * 장소를 일정에 담습니다.
     *
     * visitOrder 는 서비스가 그날 마지막 순서를 조회해 계산한 뒤 넘깁니다.
     * 요청에는 담기지 않습니다.
     *
     * 1 미만을 거부하는 것은 정렬 때문이 아닙니다.
     * 상대 순서만 보므로 음수여도 정렬 자체는 정확합니다.
     * 다만 그날 마지막 + 1 로 채우는 규칙에서는 1 미만이 나올 수 없으므로,
     * 그런 값이 들어왔다는 것은 계산이 어딘가에서 틀렸다는 신호입니다.
     * 잘못된 값이 행으로 남기 전에 여기서 멈춥니다.
     */
    public static ItineraryStop create(UUID accountId, UUID placeId, LocalDateTime visitAt,
                                       UUID petId, Integer visitOrder, String memo) {
        if (accountId == null || placeId == null) {
            throw new IllegalArgumentException("accountId 와 placeId 는 필수입니다.");
        }
        if (visitAt == null) {
            throw new IllegalArgumentException("visitAt 은 필수입니다.");
        }
        if (visitOrder == null || visitOrder < 1) {
            throw new IllegalArgumentException("visitOrder 는 1 이상이어야 하며 서버가 채웁니다.");
        }
        return new ItineraryStop(accountId, placeId, visitAt, petId, visitOrder, memo);
    }
}
