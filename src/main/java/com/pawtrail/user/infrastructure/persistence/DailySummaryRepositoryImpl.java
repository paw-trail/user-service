package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.DailySummaryId;
import com.pawtrail.user.domain.repository.DailySummaryRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.DailySummaryJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 *
 * save 가 곧 upsert 입니다.
 * 복합 키가 이미 있으면 스프링 데이터가 merge 로 처리하므로
 * 같은 날짜를 다시 요약해도 행이 늘지 않습니다.
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
}
