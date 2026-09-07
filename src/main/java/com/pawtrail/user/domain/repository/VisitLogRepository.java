package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.VisitLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 방문 기록을 저장하고 찾아오는 약속입니다.
 */
public interface VisitLogRepository {

    VisitLog save(VisitLog visitLog);

    Optional<VisitLog> findById(UUID id);

    void delete(VisitLog visitLog);

    // 그 사람의 방문 기록 수를 셈
    // 마이페이지 상단의 stats.visitCount 가 이 값임
    //
    // "갔다고 사용자가 확인한 것" 만 세짐
    // 담아두고 안 간 곳은 itinerary_stop 에만 있어 여기 안 걸림
    long countByAccountId(UUID accountId);

    // 그 사람의 방문 기록을 전부 돌려줌
    //
    // GET /api/v1/visits 가 씀
    // 마이페이지 동반 기록과 「방문한 장소」 두 화면이 같은 API 를 씀
    //
    // 정렬이 두 축인 것에 유의할 것
    //   날짜는 최신 먼저    목록을 열면 최근 다녀온 날이 위에 있어야 함
    //   하루 안은 시간 순    그날의 동선이 순서대로 보여야 함
    //
    // 축이 반대인 이유
    // 3개월 전 기록이 맨 위에 있으면 어제 것을 보려고 한참 내려야 하고,
    // 하루 안이 역순이면 15:00 카페가 11:00 공원보다 위에 떠 동선이 거꾸로 보임
    //
    // 페이징하지 않는 것은 즐겨찾기와 같음
    // 프론트가 응답 전체를 placeType 으로 세어 카테고리 칩을 만들고 0건이면 안 그림
    List<VisitLog> findAllByAccountIdOrderByVisitedAt(UUID accountId);

    // 그 사람의 방문 기록 하나를 찾음
    //
    // DELETE /api/v1/visits/{visitId} 가 씀
    //
    // findById 를 쓰지 않는 이유
    // 경로로 받는 visitId 는 특정 사람의 기록 식별자라 그대로 조회하면 남의 행이 나옴
    // 조회에서 걸러 두면 서비스가 소유권 대조를 따로 하지 않아도 되고,
    // 그 대조를 잊는 실수가 생길 자리 자체가 없어짐
    //
    // 남의 것과 없는 것을 구분하지 않음
    // 403 을 내면 "그 visitId 는 존재하는데 네 것이 아니다" 를 알려주는 셈이라
    // 식별자를 넣어 보며 존재를 확인할 수 있게 됨
    Optional<VisitLog> findByIdAndAccountId(UUID id, UUID accountId);

    // 그 일정으로 만든 방문 기록이 있는지 찾음
    //
    // POST /api/v1/visits 가 씀
    // 같은 일정 카드에서 [다녀왔어요] 를 두 번 눌러도 성공으로 넘기기 위한 조회임
    //
    // 조회로 거르는 이유
    // uq_visit_log_itinerary_stop 이 중복을 막기는 하지만
    // 그 위반을 예외로 잡는 방식은 쓸 수 없음
    // 기본 키를 애플리케이션이 만들어 넣어 INSERT 가 커밋 직전에 나가고,
    // 앞당겨도 그 예외가 트랜잭션에 rollback-only 를 남겨 커밋이 거부됨
    // 즐겨찾기에서 실물로 겪고 조회 방식으로 바꾼 자리임
    Optional<VisitLog> findByItineraryStopId(UUID itineraryStopId);
}
