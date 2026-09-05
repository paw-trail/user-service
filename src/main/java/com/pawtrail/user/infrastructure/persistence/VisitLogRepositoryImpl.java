package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.VisitLog;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.VisitLogJpaRepository;
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
}
