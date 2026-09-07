package com.pawtrail.user.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.application.dto.input.ItineraryCreateInput;
import com.pawtrail.user.application.dto.input.ItineraryUpdateInput;
import com.pawtrail.user.application.dto.output.ItineraryCardOutput;
import com.pawtrail.user.application.dto.output.ItineraryCreateOutput;
import com.pawtrail.user.domain.exception.UserErrorCode;
import com.pawtrail.user.domain.model.ItineraryStop;
import com.pawtrail.user.domain.model.VisitLog;
import com.pawtrail.user.domain.provider.PlaceProvider;
import com.pawtrail.user.domain.provider.ReviewProvider;
import com.pawtrail.user.domain.provider.VerdictProvider;
import com.pawtrail.user.domain.provider.dto.PlaceData;
import com.pawtrail.user.domain.provider.dto.VerdictData;
import com.pawtrail.user.domain.repository.ItineraryStopRepository;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날짜 단위 일정을 다루는 서비스입니다.
 *
 * 하루가 곧 일정입니다.
 * 제목이 있는 여행을 묶는 표가 없어 목록도 담기도 날짜 하나를 기준으로 돕니다.
 *
 * 날짜로 고르는 조회가 셋인데 모두 반열림 구간을 씁니다.
 * 시작은 포함하고 끝은 포함하지 않습니다.
 * 그 계산을 이 클래스 아래쪽 한 곳에 모아 두었습니다.
 * 목록과 마지막 순서가 같은 범위를 써야 하는데 두 곳에서 따로 계산하면
 * 한쪽만 고쳐졌을 때 조용히 어긋납니다.
 *
 * 목록은 서버가 조립합니다.
 * 즐겨찾기와 같은 세 곳(place · verdict · review)을 부르고 favorite 만 부르지 않습니다.
 * 이 화면의 카드에는 하트가 없기 때문입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryService {

    private final ItineraryStopRepository itineraryStopRepository;
    private final VisitLogRepository visitLogRepository;
    private final PlaceProvider placeProvider;
    private final VerdictProvider verdictProvider;
    private final ReviewProvider reviewProvider;

    /**
     * 장소를 일정에 담습니다.
     *
     * 장소 상세의 일정 추가 폼이 부릅니다.
     * 일정 화면에서 장소를 담는 자리는 없습니다.
     *
     * 같은 계정이 같은 장소를 같은 시각에 이미 담아 두었으면 새로 만들지 않고
     * 그 식별자를 돌려줍니다.
     * 같은 장소를 하루에 여러 번 담는 것 자체는 정상입니다.
     * 오전에 들렀다가 저녁에 다시 가는 일정이 있을 수 있어 시각이 다르면 다른 항목입니다.
     * 걸러내는 것은 시각까지 완전히 같은 경우뿐입니다.
     *
     * 표에 UNIQUE 제약이 없어 저장 자체는 막히지 않습니다.
     * 즐겨찾기처럼 "제약 위반을 예외로 못 잡아서" 조회하는 것이 아니라
     * 같은 항목을 두 번 만들지 않으려고 의도적으로 조회합니다.
     *
     * 장소가 실제로 있는지는 확인하지 않습니다.
     * 담은 뒤에 사라지는 경우를 어차피 막을 수 없어 걸러내는 자리를 목록 조립 한 곳에 모았습니다.
     * 관리자가 잘못 묶인 소스를 분리하거나 재수집에서 두 장소가 합쳐지면
     * 저장해 둔 placeId 가 없어질 수 있습니다.
     *
     * 과거 날짜도 담을 수 있습니다.
     * 지난 날짜 카드에 다녀왔어요 가 뜨는 구조 자체가 과거 일정의 존재를 전제합니다.
     */
    @Transactional
    public ItineraryCreateOutput add(UUID accountId, ItineraryCreateInput input) {
        LocalDateTime visitAt = input.visitAt();

        Optional<ItineraryStop> already = itineraryStopRepository
                .findByAccountIdAndPlaceIdAndVisitAt(accountId, input.placeId(), visitAt);

        if (already.isPresent()) {
            log.info("이미 담아 둔 일정입니다: accountId={}, stopId={}",
                    accountId, already.get().getId());
            return new ItineraryCreateOutput(already.get().getId());
        }

        int nextOrder = itineraryStopRepository.findMaxVisitOrder(
                accountId, startOfDay(visitAt), startOfNextDay(visitAt)) + 1;

        ItineraryStop saved = itineraryStopRepository.save(ItineraryStop.create(
                accountId, input.placeId(), visitAt, input.petId(), nextOrder, input.memo()));

        log.info("일정에 담았습니다: accountId={}, placeId={}, stopId={}",
                accountId, input.placeId(), saved.getId());

        return new ItineraryCreateOutput(saved.getId());
    }

    /**
     * 그날 일정 목록을 조립해 돌려줍니다.
     *
     * 순서는 방문 예정 시각 순이고, 시각이 같으면 담은 순서입니다.
     * 화면이 곧 그날의 동선이라 먼저 갈 곳이 위에 옵니다.
     *
     * 판정이 방문 기록과 성격이 다릅니다.
     * 그쪽은 다녀온 시점을 박아 둔 값이라 저장된 것을 쓰지만,
     * 일정은 아직 안 간 곳이라 지금 조건으로 판정해야 합니다. 즐겨찾기와 같습니다.
     *
     * 셋의 실패를 다르게 다룹니다.
     *   place    목록 전체가 502 로 실패합니다. 장소 이름이 없으면 카드가 성립하지 않습니다.
     *   verdict  배지와 준비물만 비고 목록은 그대로 내려갑니다.
     *   review   별점만 비고 목록은 그대로 내려갑니다.
     *
     * 페이징하지 않습니다.
     * 하루치라 건수가 애초에 작습니다.
     */
    @Transactional(readOnly = true)
    public List<ItineraryCardOutput> getMyItinerary(UUID accountId, LocalDate date) {
        List<ItineraryStop> stops = itineraryStopRepository.findAllByAccountIdAndDay(
                accountId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        if (stops.isEmpty()) {
            return List.of();
        }

        List<UUID> placeIds = stops.stream().map(ItineraryStop::getPlaceId).distinct().toList();

        Map<UUID, PlaceData> places = placeProvider.findByIds(placeIds);
        if (places == null) {
            log.warn("장소를 받아오지 못해 일정 목록을 내려보내지 않습니다: accountId={}", accountId);
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        Map<UUID, Map<UUID, VerdictData>> verdicts = findVerdicts(accountId, stops, placeIds);
        Map<UUID, Double> ratings = reviewProvider.findRatingsByPlaceIds(placeIds);
        Map<UUID, VisitLog> visits = findVisits(stops);

        return assemble(stops, places, verdicts, ratings, visits);
    }

    /**
     * 담아 둔 일정을 고칩니다.
     *
     * 소유권 검증을 다른 조회보다 먼저 합니다.
     * 방문 기록에서 순서를 뒤집었다가 남의 식별자가 응답에 실려 나갈 뻔한 자리라
     * "내 것인가" 를 먼저 묻고 나머지를 나중에 묻습니다.
     *
     * 날짜를 바꾸는 것은 막습니다.
     * 일정을 묶는 표가 없어 날짜가 곧 그 일정의 정체성입니다.
     * 같은 날 안에서 시각을 고치는 것은 이 일정을 손보는 것이지만
     * 다른 날로 옮기는 것은 다른 일정으로 옮기는 것이라 성격이 다릅니다.
     * 화면도 날짜 하나를 골라 그날만 보는 구조라 옮기면 보고 있던 목록에서 카드가 사라집니다.
     *
     * 서버가 막아야 합니다.
     * 수정 폼에 날짜 칸이 없어도 visit_at 이 한 컬럼이라 요청은 일시를 통째로 보냅니다.
     * "시각만 바꾼다" 를 요청 형태로 표현할 방법이 없습니다.
     *
     * 고친 시각에 같은 장소가 이미 담겨 있으면 거부합니다.
     * 담기는 그 상태를 멱등으로 접지만 수정은 두 행을 하나로 합칠 수 없습니다.
     * 담기에서 막아 둔 것을 수정으로 만들 수 있으면 규칙이 서 있지 않게 됩니다.
     *
     * visitOrder 는 건드리지 않습니다.
     * 정렬이 visit_at 을 먼저 보므로 시각을 고치면 자리가 저절로 옮겨지고,
     * 같은 날 안에 머무르므로 그날 마지막 + 1 이라는 규칙도 깨지지 않습니다.
     */
    @Transactional
    public void update(UUID accountId, UUID stopId, ItineraryUpdateInput input) {
        ItineraryStop stop = itineraryStopRepository.findByIdAndAccountId(stopId, accountId)
                .orElseThrow(() -> new CustomException(UserErrorCode.ITINERARY_NOT_FOUND));

        LocalDateTime visitAt = input.visitAt() == null ? stop.getVisitAt() : input.visitAt();
        UUID petId = input.petIdProvided() ? input.petId() : stop.getPetId();
        String memo = input.memoProvided() ? input.memo() : stop.getMemo();

        if (!isSameDay(stop.getVisitAt(), visitAt)) {
            log.info("일정의 날짜를 바꾸려는 요청입니다: accountId={}, stopId={}, {} -> {}",
                    accountId, stopId, stop.getVisitAt(), visitAt);
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        if (!visitAt.equals(stop.getVisitAt())) {
            boolean taken = itineraryStopRepository
                    .findByAccountIdAndPlaceIdAndVisitAt(accountId, stop.getPlaceId(), visitAt)
                    .filter(other -> !other.getId().equals(stopId))
                    .isPresent();

            if (taken) {
                throw new CustomException(UserErrorCode.ITINERARY_DUPLICATE);
            }
        }

        stop.update(visitAt, petId, memo);
        log.info("일정을 고쳤습니다: accountId={}, stopId={}", accountId, stopId);
    }

    /**
     * 담아 둔 일정을 뺍니다.
     *
     * 연결된 방문 기록도 함께 지웁니다.
     * visit_log 가 어느 일정에서 온 방문인지를 가리키고 있어
     * 일정만 지우면 없는 식별자를 가리키는 행이 남습니다.
     * 둘 다 하드 딜리트라 표시만 남겨 피할 수도 없습니다.
     *
     * 한 트랜잭션에서 처리합니다.
     * 중간에 실패하면 함께 되돌아가므로 일정만 지워지고 기록이 남는 상태가 생기지 않습니다.
     *
     * 참조하는 쪽을 먼저 지웁니다.
     * 외래 키가 없어 순서가 강제되지는 않지만 읽는 사람에게 자연스러운 순서입니다.
     *
     * 진입점이 둘입니다.
     *   여정 화면            담아 둔 일정을 뺌
     *   마이페이지 동반 기록    지난 기록도 지우고 싶을 수 있음
     * 뒤엣것에서 지우면 방문 기록도 함께 사라지므로 화면의 확인 문구가 그것을 밝혀야 합니다.
     *
     * 방문 기록만 지우는 것은 다른 API 입니다.
     * DELETE /api/v1/visits/{visitId} 는 일정을 남겨 둡니다.
     */
    @Transactional
    public void remove(UUID accountId, UUID stopId) {
        ItineraryStop stop = itineraryStopRepository.findByIdAndAccountId(stopId, accountId)
                .orElseThrow(() -> new CustomException(UserErrorCode.ITINERARY_NOT_FOUND));

        visitLogRepository.findByItineraryStopId(stopId)
                .ifPresent(visit -> {
                    visitLogRepository.delete(visit);
                    log.info("일정에 연결된 방문 기록도 지웁니다: stopId={}, visitId={}",
                            stopId, visit.getId());
                });

        itineraryStopRepository.delete(stop);
        log.info("일정을 뺐습니다: accountId={}, stopId={}", accountId, stopId);
    }

    /**
     * 그 범위에서 일정이 있는 날짜만 돌려줍니다.
     *
     * 달력이 씁니다.
     * 아무 날짜나 고를 수 있으면 빈 날이 대부분이므로 어느 날에 일정이 있는지를 미리 표시합니다.
     * 한 번에 한 달을 그리므로 범위가 곧 그 달의 첫날과 마지막 날입니다.
     *
     * 범위에 상한을 두지 않습니다.
     * 호출 형태가 이미 한 달로 좁혀져 있고, 한 사람의 일정이라 결과가 작습니다.
     *
     * 시작이 끝보다 늦으면 거부합니다.
     * 정상 흐름에서는 나오지 않고 조용히 빈 배열을 주면 원인이 드러나지 않습니다.
     *
     * 날짜로 접는 일을 여기서 합니다.
     * 조회 대상 자리에서 잘라내면 반환 타입 매핑이 확실하지 않습니다.
     * 받아오는 양은 한 사람의 한 달치라 하루에 세 곳씩 담아도 아흔 행 남짓입니다.
     * 리포지터리가 이른 것부터 돌려주므로 중복을 접어도 순서가 유지됩니다.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> getDates(UUID accountId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        List<LocalDateTime> visitAts = itineraryStopRepository.findVisitAtsInRange(
                accountId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        return visitAts.stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .toList();
    }

    /**
     * 목록에 붙일 판정을 받아옵니다.
     *
     * 일정마다 그 일정의 동반 예정 동물을 기준으로 삼습니다.
     * 오전에는 몽이와 공원, 오후에는 달이와 카페처럼 같은 날에도 기준이 다를 수 있습니다.
     *
     * 그래도 호출은 한 번입니다.
     * 판정 서비스가 장소 목록과 펫 목록을 함께 받아 장소마다 마리별 판정을 돌려줍니다.
     * 마리 수가 늘어도 왕복이 늘지 않습니다.
     *
     * 동반 동물이 하나도 없으면 부르지 않습니다.
     * 판정할 기준이 없어 물어볼 것이 없습니다.
     *
     * 실패해도 넘어갑니다.
     * 배지와 준비물이 없어도 "그날 어디를 가기로 했나" 라는 화면의 목적은 이뤄집니다.
     * 방문 기록의 판정과 반대인데, 그쪽은 저장하는 값이라 틀리면 영구히 남습니다.
     */
    private Map<UUID, Map<UUID, VerdictData>> findVerdicts(
            UUID accountId, List<ItineraryStop> stops, List<UUID> placeIds) {

        List<UUID> petIds = stops.stream()
                .map(ItineraryStop::getPetId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (petIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Map<UUID, VerdictData>> results =
                verdictProvider.findByPlaceIdsForPets(placeIds, petIds);

        if (results.isEmpty()) {
            log.warn("판정을 받아오지 못했습니다: accountId={}, 장소 {}건, 펫 {}마리",
                    accountId, placeIds.size(), petIds.size());
        }
        return results;
    }

    /**
     * 그 일정으로 만들어진 방문 기록을 모읍니다.
     *
     * 지난 날짜 카드의 다녀왔어요 버튼 상태를 그리는 값입니다.
     * 이미 눌렀으면 버튼 대신 방문함 표시가 떠야 하는데 그 판단 재료가 이것입니다.
     *
     * 한 일정에 방문 기록은 많아야 하나입니다.
     * uq_visit_log_itinerary_stop 이 그것을 보장하므로 식별자로 바로 담습니다.
     *
     * 일정 수만큼 조회가 나갑니다.
     * 하루치라 건수가 작아 그대로 둡니다.
     * 목록이 커지면 식별자를 모아 한 번에 묻는 형태로 바꿀 자리입니다.
     */
    private Map<UUID, VisitLog> findVisits(List<ItineraryStop> stops) {
        Map<UUID, VisitLog> visits = new LinkedHashMap<>();

        for (ItineraryStop stop : stops) {
            visitLogRepository.findByItineraryStopId(stop.getId())
                    .ifPresent(visit -> visits.put(stop.getId(), visit));
        }
        return visits;
    }

    /**
     * 네 곳에서 온 값을 카드로 맞춥니다.
     *
     * 리포지터리가 준 순서를 그대로 따릅니다.
     * 시각 순이고 시각이 같으면 담은 순서라 그것이 곧 화면 순서입니다.
     *
     * 장소를 못 찾은 카드는 목록에서 뺍니다.
     * 관리자가 잘못 묶인 소스를 분리했거나 재수집 중 두 장소가 합쳐지면
     * 저장해 둔 placeId 가 사라질 수 있습니다.
     * 이름이 없는 카드는 만들 수 없고 지도에 찍을 좌표도 없습니다.
     */
    private List<ItineraryCardOutput> assemble(List<ItineraryStop> stops,
                                               Map<UUID, PlaceData> places,
                                               Map<UUID, Map<UUID, VerdictData>> verdicts,
                                               Map<UUID, Double> ratings,
                                               Map<UUID, VisitLog> visits) {

        List<ItineraryCardOutput> cards = new ArrayList<>();
        int missing = 0;

        for (ItineraryStop stop : stops) {
            UUID placeId = stop.getPlaceId();
            PlaceData place = places.get(placeId);

            if (place == null) {
                missing++;
                continue;
            }

            VerdictData data = verdictOf(verdicts, placeId, stop.getPetId());
            VisitLog visit = visits.get(stop.getId());

            cards.add(new ItineraryCardOutput(
                    stop.getId(),
                    placeId,
                    place.name(),
                    place.imageUrl(),
                    place.lat(),
                    place.lon(),
                    place.placeType(),
                    place.supplyPoint(),
                    stop.getVisitAt(),
                    stop.getPetId(),
                    stop.getVisitOrder(),
                    stop.getMemo(),
                    verdictValueOf(data, stop.getPetId()),
                    data == null || data.requiredItems() == null
                            ? List.of() : data.requiredItems(),
                    ratings.get(placeId),
                    visit != null,
                    visit == null ? null : visit.getId()));
        }

        if (missing > 0) {
            log.warn("장소를 찾지 못해 목록에서 뺀 일정이 있습니다: {}건", missing);
        }

        return cards;
    }

    /**
     * 그 일정의 판정을 꺼냅니다.
     *
     * 장소와 동반 동물 두 겹으로 찾습니다.
     * 어느 한쪽 키가 없으면 그 조합의 판정을 못 받은 것이라 null 입니다.
     */
    private VerdictData verdictOf(Map<UUID, Map<UUID, VerdictData>> verdicts,
                                  UUID placeId, UUID petId) {
        if (petId == null) {
            return null;
        }
        Map<UUID, VerdictData> byPet = verdicts.get(placeId);
        return byPet == null ? null : byPet.get(petId);
    }

    /**
     * 카드에 실을 판정 값을 정합니다.
     *
     * 두 경우를 갈라야 합니다.
     *   동반 동물이 없음    UNKNOWN.  판단할 조건 정보가 없다는 뜻이며 정상 상태임
     *   판정을 못 받아옴    null.     호출이 실패한 것이라 화면이 다르게 안내해야 함
     *
     * 둘을 같은 값으로 내보내면 프론트가 "대표 반려동물을 설정해 주세요" 와
     * "판정을 불러오지 못했어요" 를 가려 쓸 수 없습니다.
     */
    private String verdictValueOf(VerdictData data, UUID petId) {
        if (petId == null) {
            return "UNKNOWN";
        }
        return data == null ? null : data.verdict();
    }

    /**
     * 두 일시가 같은 날인지 봅니다.
     */
    private boolean isSameDay(LocalDateTime a, LocalDateTime b) {
        return a.toLocalDate().equals(b.toLocalDate());
    }

    /**
     * 그 일시가 속한 날의 00:00 을 돌려줍니다.
     */
    private LocalDateTime startOfDay(LocalDateTime dateTime) {
        return dateTime.toLocalDate().atStartOfDay();
    }

    /**
     * 그 일시가 속한 날의 다음 날 00:00 을 돌려줍니다.
     *
     * 끝 경계로 쓰며 이 값은 포함하지 않습니다.
     * BETWEEN 은 양끝을 포함해 다음 날 00:00 짜리 일정이 함께 걸립니다.
     * 방문 예정 시각을 정하지 않으면 그 날짜의 00:00 이 들어가므로 실제로 자주 생기는 값입니다.
     */
    private LocalDateTime startOfNextDay(LocalDateTime dateTime) {
        return dateTime.toLocalDate().plusDays(1).atStartOfDay();
    }
}
