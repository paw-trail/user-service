package com.pawtrail.user.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.application.dto.input.FavoriteCreateInput;
import com.pawtrail.user.application.dto.output.FavoriteCardOutput;
import com.pawtrail.user.domain.model.Favorite;
import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.provider.PlaceProvider;
import com.pawtrail.user.domain.provider.ReviewProvider;
import com.pawtrail.user.domain.provider.VerdictProvider;
import com.pawtrail.user.domain.provider.dto.PlaceData;
import com.pawtrail.user.domain.provider.dto.VerdictData;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 즐겨찾기를 다루는 서비스입니다.
 *
 * 담기와 해제는 우리 표만 건드리지만 목록은 세 서비스를 불러 조립합니다.
 * 장소 이름과 사진은 place 가, 판정 배지와 준비물은 verdict 가,
 * 평점은 review 가 가지고 있습니다.
 *
 * 셋의 실패를 다르게 다룹니다.
 * 장소 이름이 없으면 카드가 성립하지 않아 목록 전체를 실패시키지만,
 * 판정과 평점은 없어도 "내가 담아둔 곳 목록" 이라는 화면의 목적이 이뤄집니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    /**
     * 판정할 기준이 없을 때 채우는 값입니다.
     *
     * verdict 서비스가 쓰는 네 값 중 하나이며 "판단할 조건 정보가 없음" 을 뜻합니다.
     * 대표 반려동물이 없어 아예 부르지 못한 경우도 같은 값으로 나갑니다.
     * 둘 다 사용자에게는 "지금은 판정을 보여줄 수 없다" 로 같기 때문입니다.
     */
    private static final String VERDICT_UNKNOWN = "UNKNOWN";

    private final FavoriteRepository favoriteRepository;
    private final UserProfileRepository userProfileRepository;
    private final PlaceProvider placeProvider;
    private final VerdictProvider verdictProvider;
    private final ReviewProvider reviewProvider;

    /**
     * 즐겨찾기에 담습니다.
     *
     * 이미 담아 둔 장소를 다시 담아도 성공으로 봅니다.
     * 하트는 결과 상태를 만드는 동작이라 두 번 눌러도 "담긴 상태" 가 되면
     * 사용자가 원한 것이 이뤄집니다.
     * 하트가 붙는 화면들은 응답에 isFavorite 이 실려 이미 담긴 것이 꽉 찬 하트로 보이므로,
     * 정상 흐름에서는 중복 요청이 나가지도 않습니다.
     * 더블클릭이나 두 탭에서 나가는 것인데 거기에 오류를 띄우면 오히려 이상합니다.
     *
     * 조회로 먼저 거릅니다.
     *
     * 처음에는 조회 없이 저장하고 UNIQUE 위반을 잡는 쪽으로 만들었습니다.
     * 조회와 저장 사이에 다른 요청이 끼어들 수 있다는 이유였는데, 그 방식은 되지 않습니다.
     * 제약 위반이 나면 스프링이 트랜잭션에 rollback-only 표시를 남기고,
     * 그 표시는 예외를 잡아도 지워지지 않습니다.
     * 메서드가 정상으로 끝나도 커밋이 거부되어 UnexpectedRollbackException 이 납니다.
     * 실제로 그렇게 500 이 나가는 것을 겪고 이 형태로 바꿨습니다.
     *
     * 그래서 남는 틈은 받아들입니다.
     * 같은 사람이 같은 장소를 밀리초 안에 두 번 담아야 도달하고,
     * 뚫리더라도 uq_favorite_account_place 가 막아 행은 하나만 남습니다.
     * 사용자가 보는 결과는 어느 쪽이든 "담긴 상태" 로 같습니다.
     *
     * 네이티브 INSERT 에 ON CONFLICT DO NOTHING 을 붙이면 틈이 없어지지만,
     * 그러면 이 표만 id 와 감사 컬럼 넷을 손으로 채우게 되어
     * 감사 컬럼을 자동으로 채운다는 규칙에 예외가 하나 생깁니다.
     *
     * 장소가 실재하는지 확인하지 않습니다.
     * 담은 뒤에 장소가 사라지는 경우는 그 검사로 막지 못하고,
     * 목록 조회에서 없는 것을 걸러내면 두 경우가 함께 처리됩니다.
     * 같은 문제를 두 군데서 막지 않기 위해서입니다.
     */
    @Transactional
    public void add(UUID accountId, FavoriteCreateInput input) {
        boolean already = favoriteRepository
                .findByAccountIdAndPlaceId(accountId, input.placeId())
                .isPresent();

        if (already) {
            log.info("이미 담아 둔 장소입니다: accountId={}, placeId={}",
                    accountId, input.placeId());
            return;
        }

        favoriteRepository.save(Favorite.create(accountId, input.placeId(), input.memo()));

        log.info("즐겨찾기에 담았습니다: accountId={}, placeId={}", accountId, input.placeId());
    }

    /**
     * 즐겨찾기를 해제합니다.
     *
     * 담아 두지 않은 장소를 해제해도 성공으로 봅니다.
     * 담기가 멱등인데 해제만 404 를 내면 같은 하트 버튼이 방향에 따라 다르게 동작합니다.
     * 어느 쪽이든 끝난 뒤의 상태가 "안 담긴 상태" 로 같습니다.
     *
     * favoriteId 가 아니라 placeId 로 찾습니다.
     * 하트를 누르는 자리가 장소 카드라 프론트가 아는 값이 placeId 뿐입니다.
     */
    @Transactional
    public void remove(UUID accountId, UUID placeId) {
        Optional<Favorite> favorite =
                favoriteRepository.findByAccountIdAndPlaceId(accountId, placeId);

        if (favorite.isEmpty()) {
            log.info("담아 두지 않은 장소입니다: accountId={}, placeId={}", accountId, placeId);
            return;
        }

        favoriteRepository.delete(favorite.get());
        log.info("즐겨찾기를 해제했습니다: accountId={}, placeId={}", accountId, placeId);
    }

    /**
     * 즐겨찾기 목록을 조립해 돌려줍니다.
     *
     * 페이징하지 않습니다.
     * 프론트가 응답 전체를 placeType 으로 세어 카테고리 칩을 만들고
     * 0건인 칩은 그리지 않기로 되어 있어, 페이지로 자르면 그 규칙이 깨집니다.
     *
     * 하나도 없으면 외부를 부르지 않고 빈 목록을 돌려줍니다.
     * 물어볼 것이 없는 요청을 세 번 보낼 이유가 없습니다.
     */
    @Transactional(readOnly = true)
    public List<FavoriteCardOutput> getMyFavorites(UUID accountId) {
        List<Favorite> favorites =
                favoriteRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId);

        if (favorites.isEmpty()) {
            return List.of();
        }

        List<UUID> placeIds = favorites.stream().map(Favorite::getPlaceId).toList();

        Map<UUID, PlaceData> places = placeProvider.findByIds(placeIds);
        if (places == null) {
            log.warn("장소를 받아오지 못해 즐겨찾기 목록을 내려보내지 않습니다: accountId={}", accountId);
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        Map<UUID, VerdictData> verdicts = findVerdicts(accountId, placeIds);
        Map<UUID, Double> ratings = reviewProvider.findRatingsByPlaceIds(placeIds);

        return assemble(favorites, places, verdicts, ratings);
    }

    /**
     * 그 장소를 담아 둔 사람들의 계정 식별자를 돌려줍니다.
     *
     * GET /internal/favorites?placeId= 가 씁니다.
     * 조건이 바뀌었을 때 notification 이 알릴 대상자를 찾는 경로입니다.
     *
     * 소유권을 검증하지 않습니다.
     * 여러 사람을 한 번에 찾는 조회라 "내 것" 이라는 개념이 없습니다.
     * GET /internal/users?ids= 와 같은 성격입니다.
     */
    @Transactional(readOnly = true)
    public Page<UUID> getAccountIdsByPlaceId(UUID placeId, Pageable pageable) {
        return favoriteRepository.findAccountIdsByPlaceId(placeId, pageable);
    }

    /**
     * 대표 반려동물을 기준으로 판정을 받아옵니다.
     *
     * 대표가 없으면 verdict 를 부르지 않고 전부 UNKNOWN 으로 채운 Map 을 만듭니다.
     * 판정할 기준이 없어 물어볼 것이 없고, 응답의 verdict 는 필수 값이라
     * 무언가로는 채워야 합니다.
     * 펫이 0마리이거나 대표를 해제한 경우가 여기에 해당합니다.
     *
     * 채우는 값이 UNKNOWN 인 것은 방문 기록과 대칭입니다.
     * visit_log.verdict_at_visit 도 펫이 0마리면 UNKNOWN 으로 남습니다.
     *
     * 여기서 채우고 조립에서 채우지 않는 데 뜻이 있습니다.
     * 조립은 "키가 있으면 그 값, 없으면 판정을 못 받은 것" 하나만 보면 되고,
     * 대표가 있는지 없는지를 다시 따지지 않습니다.
     *
     * 그래서 조립 쪽의 null 은 뜻이 하나로 좁혀집니다.
     *
     *   UNKNOWN   대표가 없거나, 판정할 조건 정보가 없는 장소
     *   null      판정을 못 불러왔음
     *
     * 프론트는 앞엣것의 두 경우를 GET /users/me 의 defaultPetId 로 가릅니다.
     * 대표가 없으면 "대표 반려동물을 설정해 주세요" 이고,
     * 있는데 UNKNOWN 이면 "조건 정보 없음" 입니다.
     *
     * 프로필이 없으면 그때도 부르지 않습니다.
     * 가입 직후 account.created 가 아직 처리되지 않았을 때인데,
     * 즐겨찾기가 있으려면 프로필이 먼저 있어야 하므로 사실상 오지 않습니다.
     */
    private Map<UUID, VerdictData> findVerdicts(UUID accountId, List<UUID> placeIds) {
        UUID defaultPetId = userProfileRepository.findById(accountId)
                .map(UserProfile::getDefaultPetId)
                .orElse(null);

        if (defaultPetId == null) {
            return unknownFor(placeIds);
        }

        return verdictProvider.findByPlaceIds(placeIds, defaultPetId);
    }

    /**
     * 모든 장소를 UNKNOWN 으로 채운 Map 을 만듭니다.
     *
     * 준비물은 빈 목록입니다.
     * 판정을 하지 않았으므로 챙길 것을 알 방법이 없습니다.
     */
    private Map<UUID, VerdictData> unknownFor(List<UUID> placeIds) {
        VerdictData unknown = new VerdictData(VERDICT_UNKNOWN, List.of());

        Map<UUID, VerdictData> map = new LinkedHashMap<>();
        for (UUID placeId : placeIds) {
            map.put(placeId, unknown);
        }
        return map;
    }

    /**
     * 네 곳에서 온 값을 카드로 맞춥니다.
     *
     * favorite 의 순서를 그대로 따릅니다.
     * 리포지터리가 최근에 담은 것부터 돌려주므로 그것이 곧 화면 순서입니다.
     *
     * 장소를 못 찾은 카드는 목록에서 뺍니다.
     * 관리자가 잘못 묶인 소스를 분리했거나 재수집 중 두 장소가 합쳐지면
     * 담아 둔 placeId 가 사라질 수 있습니다.
     * 이름이 없는 카드는 명세상 만들 수 없고 보여줄 값도 없습니다.
     *
     * 판정이 없으면 verdict 를 null 로 두고 준비물을 빈 목록으로 둡니다.
     * 여기까지 왔을 때 판정이 없다는 것은 "못 불러왔다" 하나뿐입니다.
     * 대표 반려동물이 없는 경우는 findVerdicts 가 미리 UNKNOWN 으로 채워 두기 때문입니다.
     */
    private List<FavoriteCardOutput> assemble(List<Favorite> favorites,
                                              Map<UUID, PlaceData> places,
                                              Map<UUID, VerdictData> verdicts,
                                              Map<UUID, Double> ratings) {

        List<FavoriteCardOutput> cards = new ArrayList<>();
        int missing = 0;

        for (Favorite favorite : favorites) {
            UUID placeId = favorite.getPlaceId();
            PlaceData place = places.get(placeId);

            if (place == null) {
                missing++;
                continue;
            }

            VerdictData verdict = verdicts.get(placeId);

            cards.add(new FavoriteCardOutput(
                    placeId,
                    place.name(),
                    place.placeType(),
                    place.imageUrl(),
                    verdict == null ? null : verdict.verdict(),
                    verdict == null ? List.of() : verdict.requiredItems(),
                    ratings.get(placeId),
                    favorite.getMemo(),
                    favorite.getCreatedAt()));
        }

        if (missing > 0) {
            log.warn("장소를 찾지 못해 목록에서 뺀 즐겨찾기가 있습니다: {}건", missing);
        }

        return cards;
    }
}
