package com.pawtrail.user.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.application.dto.output.RecentPlaceCardOutput;
import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.provider.PlaceProvider;
import com.pawtrail.user.domain.provider.ReviewProvider;
import com.pawtrail.user.domain.provider.VerdictProvider;
import com.pawtrail.user.domain.provider.dto.PlaceData;
import com.pawtrail.user.domain.provider.dto.VerdictData;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.domain.repository.RecentPlaceStore;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근에 본 장소를 다루는 서비스입니다.
 *
 * 표를 만들지 않고 Redis 목록을 씁니다.
 * 최근 스물 곳만 남기면 되는 값이라 오래된 것이 밀려나야 하는데,
 * 데이터베이스에 두면 그 정리를 우리가 짜야 하고 남길 가치도 없는 이력이 쌓입니다.
 *
 * 쌓는 쪽은 프론트가 부릅니다.
 * 장소 상세를 열거나 검색 결과에서 장소를 누를 때 자동으로 보냅니다.
 *
 * 장소 서비스가 조회 중에 우리를 부르는 방법도 있었으나 그렇게 하지 않았습니다.
 * 조회는 자주 불리는 길인데 거기에 쓰기를 끼워 넣게 되기 때문입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecentPlaceService {

    private final RecentPlaceStore recentPlaceStore;
    private final UserProfileRepository userProfileRepository;
    private final FavoriteRepository favoriteRepository;
    private final PlaceProvider placeProvider;
    private final VerdictProvider verdictProvider;
    private final ReviewProvider reviewProvider;

    /**
     * 장소를 최근 목록 맨 앞에 놓습니다.
     *
     * 장소가 실제로 있는지 확인하지 않습니다.
     * 담은 뒤에 사라지는 경우를 어차피 막을 수 없어 걸러내는 자리를 목록 조회 한 곳에 모았습니다.
     * 즐겨찾기와 일정도 같은 판단을 하고 있습니다.
     *
     * 응답에 담을 것이 없습니다.
     * 프론트가 화면을 여는 김에 부르는 것이라 결과를 기다리지도 않습니다.
     */
    public void record(UUID accountId, UUID placeId) {
        recentPlaceStore.push(accountId, placeId);
        log.info("최근 본 장소에 담았습니다: accountId={}, placeId={}", accountId, placeId);
    }

    /**
     * 최근에 본 장소를 카드로 조립해 돌려줍니다.
     *
     * 즐겨찾기와 같은 세 곳을 부르고 즐겨찾기 표를 하나 더 봅니다.
     * 그쪽 목록은 그 표에서 나와 거기 있는 것이 곧 담긴 것이지만,
     * 여기는 목록이 다른 곳에서 나오므로 어느 것을 담아뒀는지 따로 확인해야 합니다.
     *
     * 판정은 대표 반려동물 한 마리 기준입니다.
     * 방문 기록이나 일정처럼 항목마다 동물이 다르지 않고,
     * 지금 이 장소를 내 대표 아이와 갈 수 있는지를 묻는 것뿐입니다.
     *
     * 셋의 실패를 다르게 다룹니다.
     *   장소   목록 전체가 실패합니다. 이름이 없으면 카드가 성립하지 않습니다.
     *   판정   배지와 준비물만 비고 목록은 그대로 내려갑니다.
     *   후기   별점만 비고 목록은 그대로 내려갑니다.
     *
     * 없는 장소는 목록에서 빠집니다.
     * Redis 에서 지우지는 않습니다.
     * 조회가 실패해 일부만 온 경우와 장소가 정말 사라진 경우를 구분할 수 없어,
     * 그것을 근거로 지우면 멀쩡한 이력이 사라집니다.
     * 상한이 있어 스무 곳을 더 보면 저절로 밀려나기도 합니다.
     */
    @Transactional(readOnly = true)
    public List<RecentPlaceCardOutput> getRecent(UUID accountId, int size) {
        List<UUID> placeIds = recentPlaceStore.findRecent(accountId, size);

        if (placeIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, PlaceData> places = placeProvider.findByIds(placeIds);
        if (places == null) {
            log.warn("장소를 받아오지 못해 최근 목록을 내려보내지 않습니다: accountId={}", accountId);
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        Map<UUID, VerdictData> verdicts = findVerdicts(accountId, placeIds);
        Map<UUID, Double> ratings = reviewProvider.findRatingsByPlaceIds(placeIds);
        Set<UUID> favorites = favoriteRepository
                .findPlaceIdsByAccountIdAndPlaceIdIn(accountId, placeIds);

        return assemble(placeIds, places, verdicts, ratings, favorites);
    }

    /**
     * 대표 반려동물 기준으로 판정을 받아옵니다.
     *
     * 대표가 없으면 부르지 않습니다.
     * 판정할 기준이 없어 물어볼 것이 없고, 조립할 때 알 수 없음으로 채웁니다.
     *
     * 지금은 대표 지정이 막혀 있어 이 값이 언제나 비어 있습니다.
     * 반려동물 서비스가 없어 그 아이가 정말 내 것인지 확인할 수단이 없기 때문입니다.
     * 그쪽이 생기면 지정이 열리고 이 경로도 함께 살아납니다.
     *
     * 실패해도 넘어갑니다.
     * 배지가 없어도 최근에 본 곳을 다시 찾는다는 화면의 목적은 이뤄집니다.
     */
    private Map<UUID, VerdictData> findVerdicts(UUID accountId, List<UUID> placeIds) {
        UUID defaultPetId = userProfileRepository.findById(accountId)
                .map(UserProfile::getDefaultPetId)
                .orElse(null);

        if (defaultPetId == null) {
            return Map.of();
        }

        Map<UUID, VerdictData> results = verdictProvider.findByPlaceIds(placeIds, defaultPetId);

        if (results.isEmpty()) {
            log.warn("판정을 받아오지 못했습니다: accountId={}, 장소 {}건", accountId, placeIds.size());
        }
        return results;
    }

    /**
     * 네 곳에서 온 값을 카드로 맞춥니다.
     *
     * Redis 가 준 순서를 그대로 따릅니다.
     * 가장 최근에 본 것이 맨 앞이고 화면도 그 순서로 보여 줍니다.
     *
     * 장소를 못 찾은 카드는 목록에서 뺍니다.
     * 관리자가 잘못 묶인 소스를 분리했거나 재수집 중 두 장소가 합쳐지면
     * 담아 둔 식별자가 사라질 수 있습니다.
     */
    private List<RecentPlaceCardOutput> assemble(List<UUID> placeIds,
                                                 Map<UUID, PlaceData> places,
                                                 Map<UUID, VerdictData> verdicts,
                                                 Map<UUID, Double> ratings,
                                                 Set<UUID> favorites) {

        List<RecentPlaceCardOutput> cards = new ArrayList<>();
        int missing = 0;

        for (UUID placeId : placeIds) {
            PlaceData place = places.get(placeId);

            if (place == null) {
                missing++;
                continue;
            }

            VerdictData data = verdicts.get(placeId);

            cards.add(new RecentPlaceCardOutput(
                    placeId,
                    place.name(),
                    place.placeType(),
                    place.imageUrl(),
                    verdictValueOf(data, verdicts),
                    data == null || data.requiredItems() == null
                            ? List.of() : data.requiredItems(),
                    ratings.get(placeId),
                    favorites.contains(placeId)));
        }

        if (missing > 0) {
            log.warn("장소를 찾지 못해 목록에서 뺀 최근 장소가 있습니다: {}건", missing);
        }

        return cards;
    }

    /**
     * 카드에 실을 판정 값을 정합니다.
     *
     * 두 경우를 갈라야 합니다.
     *   대표 반려동물이 없음   알 수 없음. 판단할 조건 정보가 없다는 뜻이며 정상 상태입니다.
     *   판정을 못 받아옴      비어 있음. 호출이 실패한 것이라 화면이 다르게 안내해야 합니다.
     *
     * 둘을 같은 값으로 내보내면 프론트가 대표 반려동물을 설정해 달라는 말과
     * 판정을 불러오지 못했다는 말을 가려 쓸 수 없습니다.
     *
     * 판정 전체가 비어 있으면 앞엣것으로 봅니다.
     * 대표가 없을 때 아예 부르지 않아 그 경우에만 통째로 비기 때문입니다.
     */
    private String verdictValueOf(VerdictData data, Map<UUID, VerdictData> verdicts) {
        if (verdicts.isEmpty()) {
            return "UNKNOWN";
        }
        return data == null ? null : data.verdict();
    }
}
