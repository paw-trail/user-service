package com.pawtrail.user.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 단위 AI 요약문입니다.
 *
 * 마이페이지 동반 기록의 날짜 카드에 붙습니다.
 * 사용자가 AI 요약하기 를 눌러야 생성되며 자동 생성이나 배치를 두지 않습니다.
 * 남용 방지로 같은 날짜 재생성에 쿨다운 1분, 계정당 하루 20건 상한을 두는데
 * 그 값들은 Redis 에 있어 이 표에는 없습니다.
 *
 * 대리 키 없이 복합 기본 키인 것이 곧 "하루에 한 줄" 을 강제하는 장치입니다.
 * upsert 만 하면 중복이 원천적으로 생기지 않습니다.
 *
 * 다만 그 강제가 성립하려면 visit_date 에 시각이 섞이지 않아야 합니다.
 * 컬럼은 timestamp 인데 뜻은 날짜이므로,
 * 같은 날의 09:30 과 14:12 가 들어오면 복합 키가 달라져 두 행이 생깁니다.
 * 그래서 만드는 문에서 LocalDate 를 받아 atStartOfDay 로 한 번만 변환합니다.
 * 조회도 DailySummaryId.of 가 같은 변환을 쓰므로 저장과 조회의 짝이 맞습니다.
 *
 * 하드 딜리트입니다.
 */
@Entity
@Table(name = "daily_summary")
@IdClass(DailySummaryId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySummary extends BaseEntity {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    // 요약 대상 날짜임
    //
    // 뜻은 날짜이고 시각 부분은 항상 00:00 임
    // 타입이 LocalDateTime 인 것은 "시각은 전부 timestamp" 라는 전역 규약을 따른 것이고,
    // 컬럼을 date 로 바꾸려면 새 마이그레이션 번호가 필요함
    //
    // 이 필드를 직접 채우는 경로는 없음
    // create 와 DailySummaryId.of 만 값을 만들며 둘 다 LocalDate 를 받음
    @Id
    @Column(name = "visit_date", nullable = false, updatable = false)
    private LocalDateTime visitDate;

    // LLM 이 만든 요약문임
    // 길이를 예측할 수 없어 text 로 둠
    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    // 생성 시각임
    // 갱신하기 를 언제 눌렀는지가 이 값임
    // BaseEntity 의 updatedAt 과 값이 같아 보이지만 뜻이 다름
    // updatedAt 은 행이 바뀐 시각이고 이 값은 요약을 만든 시각임
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    private DailySummary(UUID accountId, LocalDateTime visitDate,
                         String summary, LocalDateTime generatedAt) {
        this.accountId = accountId;
        this.visitDate = visitDate;
        this.summary = summary;
        this.generatedAt = generatedAt;
    }

    /**
     * 요약을 만듭니다.
     *
     * visitDate 를 LocalDate 로 받아 00:00 으로 자릅니다.
     * 부르는 쪽이 자르게 두면 잊는 순간 같은 날짜에 여러 행이 생기고,
     * 그 뒤로는 findById 와 upsert 가 어느 행을 가리킬지 정해지지 않습니다.
     */
    public static DailySummary create(UUID accountId, LocalDate visitDate,
                                      String summary, LocalDateTime generatedAt) {
        if (accountId == null || visitDate == null) {
            throw new IllegalArgumentException("accountId 와 visitDate 는 필수입니다.");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary 는 필수입니다.");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt 은 필수입니다.");
        }
        return new DailySummary(accountId, visitDate.atStartOfDay(), summary, generatedAt);
    }
}
