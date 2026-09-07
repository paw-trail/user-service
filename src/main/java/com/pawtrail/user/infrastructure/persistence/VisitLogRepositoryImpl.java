package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.VisitLog;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.VisitLogJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class VisitLogRepositoryImpl implements VisitLogRepository {

    private final VisitLogJpaRepository visitLogJpaRepository;

    @Override
    public VisitLog save(VisitLog visitLog) {
        return visitLogJpaRepository.save(visitLog);
    }

    @Override
    public Optional<VisitLog> findById(UUID id) {
        return visitLogJpaRepository.findById(id);
    }

    @Override
    public void delete(VisitLog visitLog) {
        visitLogJpaRepository.delete(visitLog);
    }

    @Override
    public long countByAccountId(UUID accountId) {
        return visitLogJpaRepository.countByAccountId(accountId);
    }

    @Override
    public List<VisitLog> findAllByAccountIdOrderByVisitedAt(UUID accountId) {
        return visitLogJpaRepository.findAllByAccountIdOrderByVisitedAt(accountId);
    }

    @Override
    public Optional<VisitLog> findByIdAndAccountId(UUID id, UUID accountId) {
        return visitLogJpaRepository.findByIdAndAccountId(id, accountId);
    }

    @Override
    public Optional<VisitLog> findByItineraryStopId(UUID itineraryStopId) {
        return visitLogJpaRepository.findByItineraryStopId(itineraryStopId);
    }

    @Override
    public List<VisitLog> findAllByAccountIdAndDay(UUID accountId,
                                                   LocalDateTime dayStart,
                                                   LocalDateTime dayEnd) {
        return visitLogJpaRepository.findAllByAccountIdAndDay(accountId, dayStart, dayEnd);
    }
    @Override
    public int deleteAllByAccountId(UUID accountId) {
        return visitLogJpaRepository.deleteAllByAccountId(accountId);
    }
}
