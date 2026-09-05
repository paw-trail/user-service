package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.ItineraryStop;
import java.util.Optional;
import java.util.UUID;

/**
 * 일정을 저장하고 찾아오는 약속입니다.
 *
 * 지금은 최소한만 둡니다.
 * 날짜별 목록 조회와 그날 마지막 순서 조회는 그 API 를 만드는 이슈에서 더합니다.
 */
public interface ItineraryStopRepository {

    ItineraryStop save(ItineraryStop itineraryStop);

    Optional<ItineraryStop> findById(UUID id);

    void delete(ItineraryStop itineraryStop);
}
