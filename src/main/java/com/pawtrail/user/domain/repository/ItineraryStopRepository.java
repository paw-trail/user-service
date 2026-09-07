package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.ItineraryStop;
import java.util.Optional;
import java.util.UUID;

/**
 * 일정을 저장하고 찾아오는 약속입니다.
 *
 * 날짜별 목록 조회와 그날 마지막 순서 조회는 일정 API 를 만드는 이슈에서 더합니다.
 */
public interface ItineraryStopRepository {

    ItineraryStop save(ItineraryStop itineraryStop);

    Optional<ItineraryStop> findById(UUID id);

    void delete(ItineraryStop itineraryStop);

    // 그 사람의 일정 하나를 찾음
    //
    // POST /api/v1/visits 가 씀
    // [다녀왔어요] 를 누르면 그 일정에서 place_id · visit_at · pet_id 를 읽어 오는데,
    // 요청에 담긴 stopId 가 정말 내 것인지를 여기서 가름
    //
    // findById 를 쓰지 않는 이유
    // 그대로 조회하면 남의 일정이 나오고, 그 값으로 방문 기록을 만들면
    // uq_visit_log_itinerary_stop 때문에 진짜 주인이 자기 일정을 못 누르게 됨
    //
    // 없으면 비어 있는 Optional 이 돌아옴
    // 부르는 쪽이 남의 것과 없는 것을 구분하지 않고 같은 응답을 냄
    Optional<ItineraryStop> findByIdAndAccountId(UUID id, UUID accountId);
}
