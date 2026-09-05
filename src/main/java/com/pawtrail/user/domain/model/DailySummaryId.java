package com.pawtrail.user.domain.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DailySummary 의 복합 기본 키입니다.
 *
 * user_db 에서 기본 키가 둘인 표는 daily_summary 하나뿐입니다.
 * account_id 와 visit_date 를 묶어 "한 사람 하루에 한 줄" 을 데이터베이스가 강제합니다.
 *
 * @IdClass 로 쓰므로 엔티티의 필드 이름과 여기 필드 이름이 정확히 같아야 합니다.
 * 다르면 기동할 때 매핑 오류가 납니다.
 *
 * @EmbeddedId 를 쓰지 않은 이유는 필드 접근이 한 겹 깊어져
 * upsert 로직이 읽기 불편해지고 다른 네 엔티티와 모양이 갈리기 때문입니다.
 *
 * 만드는 문을 of 하나로 좁혔습니다.
 * visit_date 컬럼은 timestamp 인데 뜻은 날짜이므로
 * 시각이 섞여 들어오면 복합 키가 "하루에 한 줄" 을 못 막습니다.
 * LocalDate 를 받아 atStartOfDay 로 한 번만 변환하면 그 자리가 한 곳으로 모입니다.
 * 저장과 조회가 같은 규칙으로 잘라야 짝이 맞으므로
 * DailySummary.create 와 이 메서드가 같은 변환을 씁니다.
 *
 * equals 와 hashCode 는 JPA 가 식별자를 비교할 때 씁니다.
 * 직접 만드는 대신 Lombok 을 쓰면 상속 관련 옵션까지 신경 써야 하므로 손으로 둡니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DailySummaryId implements Serializable {

    private UUID accountId;

    // 뜻은 날짜이고 시각 부분은 항상 00:00 임
    // 컬럼이 timestamp 인 것은 "시각은 전부 timestamp" 라는 전역 규약을 따른 것임
    private LocalDateTime visitDate;

    /**
     * 계정과 날짜로 식별자를 만듭니다.
     *
     * LocalDate 를 받는 이유는 시각이 섞여 들어올 자리를 없애기 위해서입니다.
     * 부르는 쪽이 LocalDateTime 을 가지고 있다면 toLocalDate 로 날짜만 넘깁니다.
     */
    public static DailySummaryId of(UUID accountId, LocalDate visitDate) {
        if (accountId == null || visitDate == null) {
            throw new IllegalArgumentException("accountId 와 visitDate 는 필수입니다.");
        }
        return new DailySummaryId(accountId, visitDate.atStartOfDay());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DailySummaryId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(visitDate, that.visitDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, visitDate);
    }
}
