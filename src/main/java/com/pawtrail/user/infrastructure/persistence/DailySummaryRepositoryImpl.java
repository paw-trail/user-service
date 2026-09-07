package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.DailySummaryId;
import com.pawtrail.user.domain.repository.DailySummaryRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.DailySummaryJpaRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 *
 * save 는 새로 만들 때만 씁니다.
 *
 * 이미 있는 요약을 고칠 때 다시 save 를 부르면 안 됩니다.
 * 식별자가 차 있는 객체는 병합으로 처리되는데, 새로 만든 객체는 생성 시각이 비어 있고
 * 그 값이 기존 행을 덮어씁니다. 생성 시각은 처음 저장할 때만 채워지므로
 * 반드시 값이 있어야 하는 컬럼이 비면서 갱신이 실패합니다.
 *
 * 그래서 부르는 쪽이 먼저 찾아보고, 있으면 엔티티의 update 를 부릅니다.
 * 변경 감지가 수정 시각만 갱신하고 생성 시각은 건드리지 않습니다.
 */
@Repository
@RequiredArgsConstructor
public class DailySummaryRepositoryImpl implements DailySummaryRepository {

    private final DailySummaryJpaRepository dailySummaryJpaRepository;

    @Override
    public DailySummary save(DailySummary dailySummary) {
        return dailySummaryJpaRepository.save(dailySummary);
    }

    @Override
    public Optional<DailySummary> findById(DailySummaryId id) {
        return dailySummaryJpaRepository.findById(id);
    }

    @Override
    public List<DailySummary> findByAccountIdAndVisitDateIn(
            UUID accountId, Collection<LocalDateTime> visitDates) {

        return dailySummaryJpaRepository.findByAccountIdAndVisitDateIn(accountId, visitDates);
    }
    @Override
    public int deleteAllByAccountId(UUID accountId) {
        return dailySummaryJpaRepository.deleteAllByAccountId(accountId);
    }
}
