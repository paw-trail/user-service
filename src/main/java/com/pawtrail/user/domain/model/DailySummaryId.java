package com.pawtrail.user.domain.model;

import java.io.Serializable;
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
 * equals 와 hashCode 는 JPA 가 식별자를 비교할 때 씁니다.
 * 직접 만드는 대신 Lombok 을 쓰면 상속 관련 옵션까지 신경 써야 하므로 손으로 둡니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DailySummaryId implements Serializable {

    private UUID accountId;

    private LocalDateTime visitDate;

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
