package com.pawtrail.user.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
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
    // 서버가 반드시 00:00 으로 고정해 넣어야 함
    // 시각이 섞이면 복합 키가 "하루에 한 줄" 을 못 막음
    //
    // API 는 visitDate 를 날짜 문자열로 받으므로
    // 00:00 을 붙이는 지점이 한 곳으로 모여 있어야 함
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
     * visitDate 는 반드시 00:00 으로 잘라 넘겨야 합니다.
     * 자르는 책임을 여기 두지 않는 이유는
     * 조회할 때도 같은 규칙으로 잘라야 하는데 그 자리가 여기가 아니기 때문입니다.
     * 한 곳에서 자르고 그 값을 조회와 저장에 함께 씁니다.
     */
    public static DailySummary create(UUID accountId, LocalDateTime visitDate,
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
        return new DailySummary(accountId, visitDate, summary, generatedAt);
    }
}
