package com.pawtrail.user.application.service;

import com.pawtrail.user.application.dto.output.DailySummaryOutput;
import com.pawtrail.user.domain.exception.UserErrorCode;
import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.DailySummaryId;
import com.pawtrail.user.domain.model.ItineraryStop;
import com.pawtrail.user.domain.model.VisitLog;
import com.pawtrail.user.domain.provider.LlmProvider;
import com.pawtrail.user.domain.provider.PlaceProvider;
import com.pawtrail.user.domain.provider.ReviewProvider;
import com.pawtrail.user.domain.provider.dto.PlaceData;
import com.pawtrail.user.domain.provider.dto.ReviewData;
import com.pawtrail.user.domain.provider.dto.SummaryData;
import com.pawtrail.user.domain.repository.DailySummaryRepository;
import com.pawtrail.user.domain.repository.ItineraryStopRepository;
import com.pawtrail.user.domain.repository.SummaryRateLimitStore;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 하루 요약을 만드는 서비스입니다.
 *
 * 사용자가 버튼을 눌러야 돕니다.
 * 자동 생성이나 배치를 두지 않는 이유는 일정을 여러 번 나눠 담기 때문입니다.
 * 변경마다 만들면 하루치를 대여섯 번 다시 만들게 되는데 중간 상태의 요약은 아무도 보지 않습니다.
 * 버튼으로 두면 생성 시점 규칙이 통째로 필요 없어지고 누른 만큼만 비용이 듭니다.
 *
 * 순서가 중요합니다.
 *
 *   하루 한도 → 쿨다운 → 재료 모으기 → 모델 호출 → 저장
 *
 * 앞의 둘을 먼저 보는 것은 모델을 부르기 전에 걸러야 비용이 안 나가기 때문입니다.
 * 한도를 쿨다운보다 앞에 두는 것은, 반대로 하면 한도를 다 쓴 사람이
 * 1분마다 쿨다운 키를 새로 만들어 의미 없는 키가 쌓이기 때문입니다.
 *
 * 실패했을 때 둘을 다르게 다룹니다.
 *   하루 한도  되돌리지 않습니다. 실패한 호출도 모델을 부른 것은 맞습니다.
 *   쿨다운     돌려줍니다. 요약을 받지도 못한 사람이 1분을 더 기다릴 이유가 없습니다.
 *
 * 되돌려도 안전한 것은 한도가 여전히 막고 있기 때문입니다.
 * 연달아 실패해도 그 횟수를 넘기면 더 부를 수 없습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DailySummaryRepository dailySummaryRepository;
    private final VisitLogRepository visitLogRepository;
    private final ItineraryStopRepository itineraryStopRepository;
    private final SummaryRateLimitStore summaryRateLimitStore;
    private final PlaceProvider placeProvider;
    private final ReviewProvider reviewProvider;
    private final LlmProvider llmProvider;

    /**
     * 그날의 요약을 만들거나 다시 만듭니다.
     *
     * 이미 있으면 덮어씁니다. 화면의 갱신하기가 같은 요청을 보냅니다.
     *
     * 재료가 하나도 없으면 만들지 않습니다.
     * 그날 다녀온 곳도 담아 둔 곳도 없으면 쓸 말이 없고,
     * 그런 날에는 화면에 버튼이 뜨지도 않으므로 정상 흐름에서는 오지 않습니다.
     *
     * 저장을 트랜잭션 안에서 합니다.
     * 모델 호출은 그 밖에서 끝난 뒤라 몇 초짜리 외부 호출이 트랜잭션을 잡고 있지 않습니다.
     */
    public DailySummaryOutput generate(UUID accountId, LocalDate visitDate) {
        if (!summaryRateLimitStore.tryConsumeDailyLimit(accountId)) {
            throw new CustomException(UserErrorCode.SUMMARY_DAILY_LIMIT);
        }

        if (!summaryRateLimitStore.tryAcquireCooldown(accountId, visitDate)) {
            throw new CustomException(UserErrorCode.SUMMARY_COOLDOWN);
        }

        try {
            SummaryData material = collect(accountId, visitDate);

            if (material.visited().isEmpty() && material.planned().isEmpty()) {
                log.info("요약할 재료가 없습니다: accountId={}, visitDate={}", accountId, visitDate);
                throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
            }

            String summary = llmProvider.summarize(material);

            if (summary == null) {
                throw new CustomException(UserErrorCode.SUMMARY_GENERATION_FAILED);
            }

            return save(accountId, visitDate, summary);

        } catch (RuntimeException e) {
            summaryRateLimitStore.releaseCooldown(accountId, visitDate);
            throw e;
        }
    }

    /**
     * 그날 재료를 모읍니다.
     *
     * 방문 기록과 일정을 따로 담습니다.
     * 앞엣것은 갔다고 확인한 것이고 뒤엣것은 담아만 둔 것이라 뜻이 다릅니다.
     * 담아두고 못 간 곳이 다녀온 것으로 요약되면 사용자가 자기 기록을 잘못 기억하게 됩니다.
     *
     * 일정에서 이미 다녀온 것은 뺍니다.
     * 다녀왔어요 를 누르면 일정은 그대로 남고 방문 기록이 하나 더 생기므로,
     * 거르지 않으면 같은 장소가 양쪽에 다 나옵니다.
     *
     * 장소를 한 번에 물어봅니다.
     * 방문과 일정의 장소를 합쳐 한 번만 부르므로 목록이 길어져도 왕복이 늘지 않습니다.
     *
     * 장소를 못 받아오면 이름이 없어 문장을 만들 수 없습니다.
     * 목록 화면과 달리 여기서는 카드를 빼는 것으로 넘어갈 수 없으므로 요청을 실패시킵니다.
     */
    private SummaryData collect(UUID accountId, LocalDate visitDate) {
        LocalDateTime dayStart = visitDate.atStartOfDay();
        LocalDateTime dayEnd = visitDate.plusDays(1).atStartOfDay();

        List<VisitLog> visits = visitLogRepository.findAllByAccountIdAndDay(
                accountId, dayStart, dayEnd);
        List<ItineraryStop> stops = itineraryStopRepository.findAllByAccountIdAndDay(
                accountId, dayStart, dayEnd);

        Set<UUID> visitedStopIds = new HashSet<>();
        for (VisitLog visit : visits) {
            if (visit.getItineraryStopId() != null) {
                visitedStopIds.add(visit.getItineraryStopId());
            }
        }

        List<UUID> placeIds = new ArrayList<>();
        for (VisitLog visit : visits) {
            placeIds.add(visit.getPlaceId());
        }
        for (ItineraryStop stop : stops) {
            if (!visitedStopIds.contains(stop.getId())) {
                placeIds.add(stop.getPlaceId());
            }
        }

        Map<UUID, PlaceData> places = placeProvider.findByIds(placeIds.stream().distinct().toList());
        if (places == null) {
            log.warn("장소를 받아오지 못해 요약을 만들지 않습니다: accountId={}", accountId);
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        List<SummaryData.PlaceMaterial> visited = new ArrayList<>();
        for (VisitLog visit : visits) {
            PlaceData place = places.get(visit.getPlaceId());
            if (place != null) {
                visited.add(new SummaryData.PlaceMaterial(
                        place.name(), place.placeType(),
                        visit.getVisitedAt().format(TIME), visit.getMemo()));
            }
        }

        List<SummaryData.PlaceMaterial> planned = new ArrayList<>();
        for (ItineraryStop stop : stops) {
            if (visitedStopIds.contains(stop.getId())) {
                continue;
            }
            PlaceData place = places.get(stop.getPlaceId());
            if (place != null) {
                planned.add(new SummaryData.PlaceMaterial(
                        place.name(), place.placeType(),
                        stop.getVisitAt().format(TIME), stop.getMemo()));
            }
        }

        List<ReviewData> reviews = reviewProvider.findByAccountIdAndPeriod(
                accountId, visitDate, visitDate);

        return new SummaryData(visitDate, visited, planned, reviews);
    }

    /**
     * 만든 문장을 저장합니다.
     *
     * 찾아보고 있으면 고치고 없으면 새로 만듭니다.
     *
     * 새로 만든 객체로 덮어쓰지 않습니다.
     * 식별자가 차 있는 객체를 넘기면 병합으로 처리되는데,
     * 방금 만든 객체는 생성 시각이 비어 있고 그 값이 기존 행을 덮어씁니다.
     * 생성 시각은 처음 저장할 때만 채워지므로 반드시 값이 있어야 하는 컬럼이 빕니다.
     * 첫 요약은 멀쩡히 되고 갱신할 때만 터지므로 드러나기 어려운 자리입니다.
     *
     * 찾아온 것을 고쳐 넘기는 것은 괜찮습니다.
     * 그 객체는 이미 저장된 적이 있어 생성 시각을 들고 있고,
     * 병합이 그것을 그대로 두고 바뀐 값만 반영합니다.
     *
     * 트랜잭션을 따로 열지 않습니다.
     * 이 메서드는 같은 클래스 안에서 불리므로 프록시를 타지 않아,
     * 애노테이션을 붙여도 아무 일이 일어나지 않습니다.
     * 찾아온 것을 고친 뒤 다시 넘기는 방식이라 변경 감지에 기대지 않아 그것으로 충분합니다.
     */
    private DailySummaryOutput save(UUID accountId, LocalDate visitDate, String summary) {
        LocalDateTime now = LocalDateTime.now();
        DailySummaryId id = DailySummaryId.of(accountId, visitDate);

        DailySummary target = dailySummaryRepository.findById(id)
                .map(existing -> {
                    existing.update(summary, now);
                    return existing;
                })
                .orElseGet(() -> DailySummary.create(accountId, visitDate, summary, now));

        DailySummary saved = dailySummaryRepository.save(target);

        log.info("하루 요약을 만들었습니다: accountId={}, visitDate={}", accountId, visitDate);
        return new DailySummaryOutput(visitDate, saved.getSummary(), saved.getGeneratedAt());
    }
}
