package com.pawtrail.user.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.application.dto.input.VisitCreateInput;
import com.pawtrail.user.application.dto.output.VisitCardOutput;
import com.pawtrail.user.application.dto.output.VisitCreateOutput;
import com.pawtrail.user.domain.enums.Verdict;
import com.pawtrail.user.domain.exception.UserErrorCode;
import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.ItineraryStop;
import com.pawtrail.user.domain.model.VisitLog;
import com.pawtrail.user.domain.provider.PlaceProvider;
import com.pawtrail.user.domain.provider.ReviewProvider;
import com.pawtrail.user.domain.provider.VerdictProvider;
import com.pawtrail.user.domain.provider.dto.PlaceData;
import com.pawtrail.user.domain.provider.dto.VerdictData;
import com.pawtrail.user.domain.repository.DailySummaryRepository;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.domain.repository.ItineraryStopRepository;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문 기록을 다루는 서비스입니다.
 *
 * 기록하기와 목록이 판정을 다르게 다룹니다.
 *
 *   기록할 때   판정을 못 받으면 요청 자체를 실패시킴
 *              verdict_at_visit 은 나중에 고칠 방법이 없어 틀린 값을 남기면 안 됨
 *   목록일 때   판정을 못 받아도 준비물만 비우고 목록은 내려보냄
 *              배지는 저장된 값을 쓰므로 영향이 없고, 준비물은 없어도 화면이 성립함
 *
 * 같은 서비스를 부르면서 실패 처리가 갈리는 것이 의도입니다.
 * 그래서 실패 처리를 공통 모듈에 두지 않고 부르는 쪽이 정하게 했습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitLogRepository visitLogRepository;
    private final ItineraryStopRepository itineraryStopRepository;
    private final FavoriteRepository favoriteRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final PlaceProvider placeProvider;
    private final VerdictProvider verdictProvider;
    private final ReviewProvider reviewProvider;

    /**
     * 방문을 기록합니다.
     *
     * 지난 날짜 일정 카드의 다녀왔어요 가 부릅니다.
     * 날짜가 지났다고 자동으로 만들지 않습니다.
     * 담아두고 안 간 곳이 방문으로 기록되면 visitCount 가 "가려고 계획한 곳 수" 가 됩니다.
     *
     * 같은 일정을 두 번 눌러도 성공하며 기존 식별자가 나갑니다.
     * uq_visit_log_itinerary_stop 이 중복을 막기는 하지만 그 위반을 예외로 잡을 수는 없습니다.
     * 기본 키를 애플리케이션이 만들어 넣어 INSERT 가 커밋 직전에 나가고,
     * 앞당겨도 그 예외가 트랜잭션에 rollback-only 를 남겨 커밋이 거부됩니다.
     * 즐겨찾기에서 실물로 겪고 조회 방식으로 바꾼 자리입니다.
     */
    @Transactional
    public VisitCreateOutput record(UUID accountId, VisitCreateInput input) {
        UUID stopId = input.itineraryStopId();

        UUID placeId = input.placeId();
        LocalDateTime visitedAt = input.visitedAt();
        UUID petId = input.petId();

        if (stopId != null) {
            Optional<VisitLog> already = visitLogRepository.findByItineraryStopId(stopId);
            if (already.isPresent()) {
                log.info("이미 기록한 일정입니다: accountId={}, stopId={}", accountId, stopId);
                return new VisitCreateOutput(already.get().getId());
            }

            ItineraryStop stop = itineraryStopRepository
                    .findByIdAndAccountId(stopId, accountId)
                    .orElseThrow(() -> new CustomException(CommonErrorCode.RESOURCE_NOT_FOUND));

            placeId = stop.getPlaceId();
            visitedAt = stop.getVisitAt();
            petId = stop.getPetId();
        }

        if (placeId == null || visitedAt == null) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        Verdict verdict = judge(placeId, petId);

        VisitLog saved = visitLogRepository.save(VisitLog.create(
                accountId, placeId, petId, visitedAt, verdict, stopId, input.memo()));

        log.info("방문을 기록했습니다: accountId={}, placeId={}, visitId={}",
                accountId, placeId, saved.getId());

        return new VisitCreateOutput(saved.getId());
    }

    /**
     * 방문 기록 목록을 조립해 돌려줍니다.
     *
     * 마이페이지 동반 기록과 방문한 장소가 같은 API 를 씁니다.
     *
     * 판정 배지는 저장된 값을 쓰므로 verdict 를 부르지 않아도 됩니다.
     * 그런데 준비물은 저장하는 컬럼이 없어 verdict 를 한 번 부릅니다.
     * 한 카드 안에서 배지는 그때의 사실이고 준비물은 지금의 안내라 성격이 갈립니다.
     *
     * 페이징하지 않습니다.
     * 프론트가 응답 전체를 placeType 으로 세어 카테고리 칩을 만들고 0건이면 안 그립니다.
     */
    @Transactional(readOnly = true)
    public List<VisitCardOutput> getMyVisits(UUID accountId) {
        List<VisitLog> visits = visitLogRepository.findAllByAccountIdOrderByVisitedAt(accountId);

        if (visits.isEmpty()) {
            return List.of();
        }

        List<UUID> placeIds = visits.stream().map(VisitLog::getPlaceId).distinct().toList();

        Map<UUID, PlaceData> places = placeProvider.findByIds(placeIds);
        if (places == null) {
            log.warn("장소를 받아오지 못해 방문 기록을 내려보내지 않습니다: accountId={}", accountId);
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        Map<UUID, Map<UUID, VerdictData>> verdicts =
                findRequiredItems(accountId, visits, placeIds);
        Map<UUID, Double> ratings = reviewProvider.findRatingsByPlaceIds(placeIds);
        Set<UUID> favorites = favoriteRepository
                .findPlaceIdsByAccountIdAndPlaceIdIn(accountId, placeIds);
        Map<LocalDateTime, String> summaries = findSummaries(accountId, visits);

        return assemble(visits, places, verdicts, ratings, favorites, summaries);
    }

    /**
     * 방문 기록을 지웁니다.
     *
     * 조회에서 소유권을 거릅니다.
     * 경로로 받는 visitId 는 특정 사람의 기록 식별자라 그대로 조회하면 남의 행이 나옵니다.
     *
     * 남의 것과 없는 것을 구분하지 않고 같은 응답을 냅니다.
     * 403 을 내면 그 visitId 가 존재한다는 것을 알려주는 셈이라
     * 식별자를 넣어 보며 존재를 확인할 수 있게 됩니다.
     */
    @Transactional
    public void remove(UUID accountId, UUID visitId) {
        VisitLog visit = visitLogRepository.findByIdAndAccountId(visitId, accountId)
                .orElseThrow(() -> new CustomException(CommonErrorCode.RESOURCE_NOT_FOUND));

        visitLogRepository.delete(visit);
        log.info("방문 기록을 지웠습니다: accountId={}, visitId={}", accountId, visitId);
    }

    /**
     * 기록할 판정을 받아옵니다.
     *
     * 반려동물이 없으면 부르지 않고 UNKNOWN 을 씁니다.
     * 판정할 기준이 없어 물어볼 것이 없고, 그 값의 뜻이 "판단할 조건 정보가 없음" 이라 맞습니다.
     *
     * 못 받아오면 요청 자체를 실패시킵니다.
     * 이 값은 조건이 바뀌어도 안 바뀌는 것이 존재 이유라 한 번 잘못 들어가면 영구히 잘못되고
     * 고치는 API 도 없습니다.
     * 다녀왔어요 는 며칠 전 일정을 지금 기록하는 동작이라 몇 분 뒤에 다시 눌러도 손해가 없습니다.
     *
     * UNKNOWN 으로 채우지 않는 이유는 그 값이 이미 "펫이 0마리" 를 뜻하기 때문입니다.
     * 서버 장애를 섞으면 나중에 그 기록을 보고 어느 쪽인지 알 방법이 없습니다.
     */
    private Verdict judge(UUID placeId, UUID petId) {
        if (petId == null) {
            return Verdict.UNKNOWN;
        }

        Map<UUID, VerdictData> results = verdictProvider.findByPlaceIds(List.of(placeId), petId);
        VerdictData data = results.get(placeId);

        if (data == null || data.verdict() == null) {
            log.warn("판정을 받아오지 못해 방문 기록을 만들지 않습니다: placeId={}, petId={}",
                    placeId, petId);
            throw new CustomException(UserErrorCode.VERDICT_UNAVAILABLE);
        }

        return toVerdict(data.verdict());
    }

    /**
     * 목록에 붙일 준비물을 받아옵니다.
     *
     * 기록할 때와 달리 실패해도 넘어갑니다.
     * 배지는 저장된 값을 쓰므로 영향이 없고 준비물은 없어도 카드가 성립합니다.
     *
     * 방문마다 그 방문에 함께 간 반려동물을 기준으로 삼습니다.
     * 3월에는 몽이와, 5월에는 달이와 갔을 수 있어 기준이 방문마다 다릅니다.
     *
     * 그래도 호출은 한 번입니다.
     * verdict 가 장소 목록과 펫 목록을 함께 받아 장소마다 마리별 판정을 돌려줍니다.
     * 마리 수가 늘어도 왕복이 늘지 않고 규칙 계산과 응답 크기만 늘어납니다.
     *
     * 펫이 하나도 없으면 부르지 않습니다.
     * 방문이 전부 펫 0마리 상태에서 기록된 경우인데 물어볼 기준이 없습니다.
     */
    private Map<UUID, Map<UUID, VerdictData>> findRequiredItems(
            UUID accountId, List<VisitLog> visits, List<UUID> placeIds) {

        List<UUID> petIds = visits.stream()
                .map(VisitLog::getPetId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (petIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Map<UUID, VerdictData>> results =
                verdictProvider.findByPlaceIdsForPets(placeIds, petIds);

        if (results.isEmpty()) {
            log.warn("준비물을 받아오지 못했습니다: accountId={}, 장소 {}건, 펫 {}마리",
                    accountId, placeIds.size(), petIds.size());
        }
        return results;
    }

    /**
     * 목록에 붙일 그날 요약을 받아옵니다.
     *
     * 날짜마다 부르지 않고 한 번에 모아 부릅니다.
     * 방문한 날이 서른 날이면 조회가 서른 번이 됩니다.
     *
     * 넘기는 날짜는 00:00 으로 자릅니다.
     * daily_summary 의 visit_date 는 timestamp 이지만 뜻은 날짜이고
     * 서버가 저장할 때 언제나 00:00 으로 고정합니다.
     * 시각이 붙은 값으로 찾으면 하나도 안 걸립니다.
     */
    private Map<LocalDateTime, String> findSummaries(UUID accountId, List<VisitLog> visits) {
        Set<LocalDateTime> dates = new HashSet<>();
        for (VisitLog visit : visits) {
            dates.add(startOfDay(visit.getVisitedAt()));
        }

        List<DailySummary> summaries =
                dailySummaryRepository.findByAccountIdAndVisitDateIn(accountId, dates);

        Map<LocalDateTime, String> map = new LinkedHashMap<>();
        for (DailySummary summary : summaries) {
            map.put(summary.getVisitDate(), summary.getSummary());
        }
        return map;
    }

    /**
     * 다섯 곳에서 온 값을 카드로 맞춥니다.
     *
     * 리포지터리가 준 순서를 그대로 따릅니다.
     * 날짜는 최신순이고 하루 안은 시간순이라 그것이 곧 화면 순서입니다.
     *
     * 장소를 못 찾은 카드는 목록에서 뺍니다.
     * 관리자가 잘못 묶인 소스를 분리했거나 재수집 중 두 장소가 합쳐지면
     * 저장해 둔 placeId 가 사라질 수 있습니다.
     * 이름이 없는 카드는 명세상 만들 수 없고 보여줄 값도 없습니다.
     *
     * 요약은 그날 첫 행에만 붙입니다.
     * daily_summary 가 하루 한 줄인데 응답은 방문 단위 배열이라
     * 그대로 두면 같은 문장이 그날 방문 수만큼 나갑니다.
     * 목록이 날짜 최신순이고 하루 안은 시간순이므로 첫 행은 그날 가장 이른 방문입니다.
     */
    private List<VisitCardOutput> assemble(List<VisitLog> visits,
                                           Map<UUID, PlaceData> places,
                                           Map<UUID, Map<UUID, VerdictData>> verdicts,
                                           Map<UUID, Double> ratings,
                                           Set<UUID> favorites,
                                           Map<LocalDateTime, String> summaries) {

        List<VisitCardOutput> cards = new ArrayList<>();
        Set<LocalDateTime> summaryUsed = new HashSet<>();
        int missing = 0;

        for (VisitLog visit : visits) {
            UUID placeId = visit.getPlaceId();
            PlaceData place = places.get(placeId);

            if (place == null) {
                missing++;
                continue;
            }

            LocalDateTime day = startOfDay(visit.getVisitedAt());
            String summary = null;
            if (summaryUsed.add(day)) {
                summary = summaries.get(day);
            }

            List<String> requiredItems = requiredItemsOf(verdicts, placeId, visit.getPetId());

            cards.add(new VisitCardOutput(
                    visit.getId(),
                    placeId,
                    place.name(),
                    place.placeType(),
                    place.imageUrl(),
                    requiredItems,
                    ratings.get(placeId),
                    visit.getVisitedAt(),
                    visit.getPetId(),
                    visit.getVerdictAtVisit().name(),
                    visit.getMemo(),
                    summary,
                    favorites.contains(placeId)));
        }

        if (missing > 0) {
            log.warn("장소를 찾지 못해 목록에서 뺀 방문 기록이 있습니다: {}건", missing);
        }

        return cards;
    }

    /**
     * 그 방문의 준비물을 꺼냅니다.
     *
     * 장소와 반려동물 두 겹으로 찾습니다.
     * 어느 한쪽 키가 없으면 그 조합의 판정을 못 받은 것이라 빈 목록을 돌려줍니다.
     *
     * 펫이 없는 방문도 빈 목록입니다.
     * 판정 기준이 없어 물어볼 수 없었던 경우입니다.
     */
    private List<String> requiredItemsOf(Map<UUID, Map<UUID, VerdictData>> verdicts,
                                         UUID placeId, UUID petId) {
        if (petId == null) {
            return List.of();
        }

        Map<UUID, VerdictData> byPet = verdicts.get(placeId);
        if (byPet == null) {
            return List.of();
        }

        VerdictData data = byPet.get(petId);
        return data == null ? List.of() : data.requiredItems();
    }

    /**
     * 그 시각이 속한 날의 00:00 을 돌려줍니다.
     */
    private LocalDateTime startOfDay(LocalDateTime dateTime) {
        return LocalDate.from(dateTime).atStartOfDay();
    }

    /**
     * verdict 가 준 문자열을 우리 enum 으로 바꿉니다.
     *
     * 모르는 값이 오면 기록을 만들지 않습니다.
     * 저장하면 되돌릴 수 없는 값이라 조용히 UNKNOWN 으로 떨어뜨리지 않습니다.
     */
    private Verdict toVerdict(String value) {
        try {
            return Verdict.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("모르는 판정 값을 받았습니다: {}", value);
            throw new CustomException(UserErrorCode.VERDICT_UNAVAILABLE);
        }
    }
}
