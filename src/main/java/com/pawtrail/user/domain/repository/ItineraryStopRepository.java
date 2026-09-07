package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.ItineraryStop;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 일정을 저장하고 찾아오는 약속입니다.
 *
 * 날짜로 고르는 조회가 넷 있는데 모두 반열림 구간을 받습니다.
 * 시작은 포함하고 끝은 포함하지 않는다는 뜻입니다.
 *
 * BETWEEN 을 쓰지 않는 이유가 있습니다.
 * SQL 의 BETWEEN 은 양끝을 포함하므로 다음 날 00:00 짜리 일정이 함께 걸립니다.
 * 방문 예정 시각을 정하지 않으면 그 날짜의 00:00 이 들어가므로 실제로 자주 생기는 값입니다.
 * 그대로 두면 9월 2일에 시각 없이 담은 일정이 9월 1일 목록에 뜨고,
 * 9월 1일의 마지막 순서 계산에도 끼어듭니다.
 *
 * 구간을 만드는 계산은 서비스 한 곳에 모읍니다.
 * 목록 조회와 마지막 순서 조회가 같은 범위를 써야 하는데
 * 두 곳에서 따로 계산하면 한쪽만 고쳐졌을 때 조용히 어긋납니다.
 */
public interface ItineraryStopRepository {

    ItineraryStop save(ItineraryStop itineraryStop);

    Optional<ItineraryStop> findById(UUID id);

    void delete(ItineraryStop itineraryStop);

    // 그 사람의 일정 하나를 찾음
    //
    // POST /api/v1/visits 와 PATCH · DELETE /api/v1/itineraries/{stopId} 가 씀
    // 요청에 담긴 stopId 가 정말 내 것인지를 여기서 가름
    //
    // findById 를 쓰지 않는 이유
    // 그대로 조회하면 남의 일정이 나오고, 그 값으로 방문 기록을 만들면
    // uq_visit_log_itinerary_stop 때문에 진짜 주인이 자기 일정을 못 누르게 됨
    //
    // 없으면 비어 있는 Optional 이 돌아옴
    // 부르는 쪽이 남의 것과 없는 것을 구분하지 않고 같은 응답을 냄
    Optional<ItineraryStop> findByIdAndAccountId(UUID id, UUID accountId);

    // 그날의 일정을 순서대로 돌려줌
    //
    // GET /api/v1/itineraries?date= 가 씀
    //
    // 정렬이 두 축인 것에 유의할 것
    //   visit_at      먼저 갈 곳이 위에 옴.  화면이 곧 그날의 동선임
    //   visit_order   시각이 같을 때만 갈림
    //
    // 두 번째 축이 필요한 이유
    // 시각을 정하지 않으면 visit_at 이 그날 00:00 이라 여럿이 같은 값이 됨
    // 그때 순서가 매번 달라지면 화면을 다시 열 때마다 카드가 뒤바뀜
    //
    // idx_itinerary_stop_account (account_id, visit_at, visit_order) 를 그대로 탐
    List<ItineraryStop> findAllByAccountIdAndDay(UUID accountId,
                                                 LocalDateTime dayStart,
                                                 LocalDateTime dayEnd);

    // 그날의 마지막 순서를 돌려줌
    //
    // POST /api/v1/itineraries 가 씀
    // 서버가 "그날 마지막 + 1" 로 visit_order 를 채우기 위한 값임
    //
    // 그날이 비어 있으면 0 이 돌아옴
    // 부르는 쪽이 1 을 더하면 자연히 첫 순서인 1 이 됨
    // 집계라 행을 읽어오지 않고, 결과가 null 이 될 수 없어 int 로 받음
    //
    // 목록을 읽어 마지막 행의 순서를 보는 방법을 쓰지 않는 이유
    // 담기 한 번에 그날 행을 전부 읽게 되고, 정렬 순서에 의존하게 됨
    // 나중에 정렬을 손대면 순서 계산이 조용히 깨짐
    //
    // 세는 방식(COUNT + 1)을 쓰지 않는 이유
    // 삭제해도 뒤의 값을 당기지 않으므로 곧바로 중복이 남음
    // 1·2·3 에서 2번을 지우면 세기로는 다음 값이 3 인데 3 이 이미 있음
    int findMaxVisitOrder(UUID accountId, LocalDateTime dayStart, LocalDateTime dayEnd);

    // 같은 계정이 같은 장소를 같은 시각에 이미 담았는지 찾음
    //
    // POST 와 PATCH 가 함께 씀
    //   담기   있으면 새로 만들지 않고 그 식별자를 돌려줌 (멱등)
    //   수정   있으면 400 으로 거부함.  두 행을 하나로 합칠 수 없기 때문
    //
    // 같은 장소를 하루에 여러 번 담는 것 자체는 정상임
    // 오전에 들렀다가 저녁에 다시 가는 일정이 있을 수 있음
    // 걸러내는 것은 시각까지 완전히 같은 경우뿐임
    //
    // 표에 UNIQUE 제약이 없어 저장 자체는 막히지 않음
    // 그래서 조회로 거르는 것이며, 즐겨찾기처럼 "제약 위반을 못 잡아서" 가 아님
    //
    // 방문 예정 시각을 정하지 않으면 둘 다 그날 00:00 이라 같은 것으로 판정됨
    // 같은 장소를 하루에 두 번 가면서 시각을 둘 다 비워 두는 경우에만 걸리고,
    // 구분하려면 시각을 넣으면 됨. 시각이 없으면 애초에 순서도 정해지지 않음
    Optional<ItineraryStop> findByAccountIdAndPlaceIdAndVisitAt(UUID accountId,
                                                                UUID placeId,
                                                                LocalDateTime visitAt);

    // 그 범위에 담긴 일정의 방문 예정 일시를 이른 것부터 돌려줌
    //
    // GET /api/v1/itineraries/dates?from=&to= 가 씀
    // 달력에서 일정이 있는 날에 표시를 찍기 위한 값임
    //
    // 날짜가 아니라 일시를 돌려주는 이유
    // 날짜로 접는 일은 서비스가 함
    // SQL 에서 자르면 반환 타입을 LocalDate 로 매핑해 주어야 하는데,
    // 그 매핑이 확실하지 않아 java.sql.Date 로 나오거나 기동에서 깨질 수 있음
    // 이 프로젝트에는 조회 대상 자리에 함수를 쓴 선례가 없음
    //
    // 받아오는 양은 한 사람의 한 달치라 작음
    // 하루에 세 곳씩 담아도 한 달에 아흔 행 남짓임
    //
    // 이른 것부터 돌려주므로 서비스가 중복을 접어도 순서가 그대로 유지됨
    List<LocalDateTime> findVisitAtsInRange(UUID accountId,
                                            LocalDateTime from,
                                            LocalDateTime toExclusive);
    // 그 계정의 일정 을 한 번에 지움
    //
    // 탈퇴 처리가 씀
    // 반환은 지운 행 수임, 이벤트 소비는 응답이 없어 로그가 유일한 흔적이라 남겨 둠
    int deleteAllByAccountId(UUID accountId);
}
