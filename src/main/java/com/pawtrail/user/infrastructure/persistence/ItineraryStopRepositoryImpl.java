package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.ItineraryStop;
import com.pawtrail.user.domain.repository.ItineraryStopRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.ItineraryStopJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class ItineraryStopRepositoryImpl implements ItineraryStopRepository {

    private final ItineraryStopJpaRepository itineraryStopJpaRepository;

    @Override
    public ItineraryStop save(ItineraryStop itineraryStop) {
        return itineraryStopJpaRepository.save(itineraryStop);
    }

    @Override
    public Optional<ItineraryStop> findById(UUID id) {
        return itineraryStopJpaRepository.findById(id);
    }

    @Override
    public void delete(ItineraryStop itineraryStop) {
        itineraryStopJpaRepository.delete(itineraryStop);
    }
}
