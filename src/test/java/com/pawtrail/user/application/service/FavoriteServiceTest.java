package com.pawtrail.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 즐겨찾기 목록을 조립하는 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 띄우지 않습니다.
 * 여기서 지키려는 것이 "세 서비스가 준 값을 어떻게 카드로 맞추는가" 하나이고
 * 그 판단에 데이터베이스가 관여하지 않기 때문입니다.
 *
 * 이 검사가 필요한 이유는 지금 place 와 verdict 와 review 가 없다는 데 있습니다.
 * 개발 중에는 로컬 스텁 서버를 띄워 눈으로 확인하지만 그 스텁은 실제 서비스가 생기면 버립니다.
 * 방문 기록과 일정이 같은 카드 컴포넌트를 쓰기로 되어 있어
 * 뒤 이슈에서 이 조립 코드를 건드리게 되는데, 그때 어긋난 것을 잡아 줄 자리가 여기입니다.
 *
 * 정렬은 검사하지 않습니다.
 * created_at DESC 로 돌려주는 것은 리포지터리가 하는 일이고
 * 서비스는 받은 순서를 그대로 씁니다.
 * 파생 쿼리 이름이 틀리면 contextLoads 가 기동에서 잡습니다.
 * 다만 서비스가 순서를 뒤바꾸지 않는지는 아래에서 함께 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private PlaceProvider placeProvider;

    @Mock
    private VerdictProvider verdictProvider;

    @Mock
    private ReviewProvider reviewProvider;

    @InjectMocks
    private FavoriteService favoriteService;

    private static final UUID ACCOUNT_ID = UUID.fromString("01a0726a-64e9-7712-9ecb-4996e2dcd75f");
    private static final UUID PET_ID = UUID.fromString("11111111-2222-7333-8444-555566667777");
    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");

    @Test
    @DisplayName("네 곳에서 온 값이 카드 아홉 필드에 그대로 담긴다")
    void 카드_조립() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 13, 0);
        givenFavorites(favorite(PLACE_A, "메모", createdAt));
        givenDefaultPet(PET_ID);

        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, new PlaceData(PLACE_A, "멍멍 카페", "CAFE", "https://img/a.jpg",
                        new BigDecimal("37.5665"), new BigDecimal("126.9780"), false)));
        when(verdictProvider.findByPlaceIds(anyCollection(), eq(PET_ID))).thenReturn(Map.of(
                PLACE_A, new VerdictData("ALLOWED", List.of("목줄 착용", "배변봉투 지참"))));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of(PLACE_A, 4.8));

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards).hasSize(1);
        FavoriteCardOutput card = cards.get(0);
        assertThat(card.placeId()).isEqualTo(PLACE_A);
        assertThat(card.name()).isEqualTo("멍멍 카페");
        assertThat(card.placeType()).isEqualTo("CAFE");
        assertThat(card.imageUrl()).isEqualTo("https://img/a.jpg");
        assertThat(card.verdict()).isEqualTo("ALLOWED");
        assertThat(card.requiredItems()).containsExactly("목줄 착용", "배변봉투 지참");
        assertThat(card.ratingAvg()).isEqualTo(4.8);
        assertThat(card.memo()).isEqualTo("메모");
        assertThat(card.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("담아 둔 것이 없으면 외부를 한 번도 부르지 않는다")
    void 빈_목록() {
        when(favoriteRepository.findAllByAccountIdOrderByCreatedAtDesc(ACCOUNT_ID))
                .thenReturn(List.of());

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards).isEmpty();
        verify(placeProvider, never()).findByIds(anyCollection());
        verify(verdictProvider, never()).findByPlaceIds(anyCollection(), any());
        verify(reviewProvider, never()).findRatingsByPlaceIds(anyCollection());
    }

    @Test
    @DisplayName("장소를 못 불러오면 목록 전체가 실패한다")
    void place_실패() {
        givenFavorites(favorite(PLACE_A, null, LocalDateTime.now()));
        when(placeProvider.findByIds(anyCollection())).thenReturn(null);

        assertThatThrownBy(() -> favoriteService.getMyFavorites(ACCOUNT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("장소가 빈 Map 으로 오면 실패가 아니라 빈 목록이다")
    void place_전부_사라짐() {
        givenFavorites(favorite(PLACE_A, null, LocalDateTime.now()));
        givenDefaultPet(null);
        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of());
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards).isEmpty();
    }

    @Test
    @DisplayName("장소가 일부만 오면 그 카드만 빠지고 나머지는 남는다")
    void place_일부_누락() {
        givenFavorites(
                favorite(PLACE_A, null, LocalDateTime.of(2026, 9, 7, 13, 0)),
                favorite(PLACE_B, null, LocalDateTime.of(2026, 9, 7, 12, 0)));
        givenDefaultPet(null);

        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_B, new PlaceData(PLACE_B, "남은 장소", "PARK", null, null, null, false)));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).placeId()).isEqualTo(PLACE_B);
    }

    @Test
    @DisplayName("판정을 못 불러오면 배지는 null 이고 준비물은 빈 목록이다")
    void verdict_실패() {
        givenFavorites(favorite(PLACE_A, null, LocalDateTime.now()));
        givenDefaultPet(PET_ID);

        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, new PlaceData(PLACE_A, "멍멍 카페", "CAFE", null, null, null, false)));
        when(verdictProvider.findByPlaceIds(anyCollection(), eq(PET_ID))).thenReturn(Map.of());
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of(PLACE_A, 4.8));

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).verdict()).isNull();
        assertThat(cards.get(0).requiredItems()).isEmpty();
        // 다른 값은 영향을 받지 않아야 함
        assertThat(cards.get(0).name()).isEqualTo("멍멍 카페");
        assertThat(cards.get(0).ratingAvg()).isEqualTo(4.8);
    }

    @Test
    @DisplayName("대표 반려동물이 없으면 판정을 부르지 않고 UNKNOWN 으로 채운다")
    void 대표_펫_없음() {
        givenFavorites(favorite(PLACE_A, null, LocalDateTime.now()));
        givenDefaultPet(null);

        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, new PlaceData(PLACE_A, "멍멍 카페", "CAFE", null, null, null, false)));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards.get(0).verdict()).isEqualTo("UNKNOWN");
        assertThat(cards.get(0).requiredItems()).isEmpty();
        verify(verdictProvider, never()).findByPlaceIds(anyCollection(), any());
    }

    @Test
    @DisplayName("평점을 못 불러와도 카드는 그대로 나가고 별점만 빈다")
    void review_실패() {
        givenFavorites(favorite(PLACE_A, null, LocalDateTime.now()));
        givenDefaultPet(null);

        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, new PlaceData(PLACE_A, "멍멍 카페", "CAFE", null, null, null, false)));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).ratingAvg()).isNull();
        assertThat(cards.get(0).name()).isEqualTo("멍멍 카페");
    }

    @Test
    @DisplayName("리포지터리가 준 순서를 서비스가 바꾸지 않는다")
    void 순서_보존() {
        givenFavorites(
                favorite(PLACE_A, null, LocalDateTime.of(2026, 9, 7, 13, 0)),
                favorite(PLACE_B, null, LocalDateTime.of(2026, 9, 7, 12, 0)));
        givenDefaultPet(null);

        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, new PlaceData(PLACE_A, "먼저", "PARK", null, null, null, false),
                PLACE_B, new PlaceData(PLACE_B, "나중", "CAFE", null, null, null, false)));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());

        List<FavoriteCardOutput> cards = favoriteService.getMyFavorites(ACCOUNT_ID);

        assertThat(cards).extracting(FavoriteCardOutput::placeId)
                .containsExactly(PLACE_A, PLACE_B);
    }

    @Test
    @DisplayName("이미 담아 둔 장소를 또 담으면 저장하지 않는다")
    void 담기_멱등() {
        when(favoriteRepository.findByAccountIdAndPlaceId(ACCOUNT_ID, PLACE_A))
                .thenReturn(Optional.of(favorite(PLACE_A, null, LocalDateTime.now())));

        favoriteService.add(ACCOUNT_ID, new FavoriteCreateInput(PLACE_A, null));

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("담아 두지 않은 장소를 해제해도 오류가 나지 않는다")
    void 해제_멱등() {
        when(favoriteRepository.findByAccountIdAndPlaceId(ACCOUNT_ID, PLACE_A))
                .thenReturn(Optional.empty());

        favoriteService.remove(ACCOUNT_ID, PLACE_A);

        verify(favoriteRepository, never()).delete(any());
    }

    // ── 아래는 준비를 돕는 것들임 ─────────────────────────────

    private void givenFavorites(Favorite... favorites) {
        when(favoriteRepository.findAllByAccountIdOrderByCreatedAtDesc(ACCOUNT_ID))
                .thenReturn(List.of(favorites));
    }

    private void givenDefaultPet(UUID petId) {
        UserProfile profile = UserProfile.create(ACCOUNT_ID, "다정이네");
        setField(profile, "defaultPetId", petId);
        when(userProfileRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(profile));
    }

    /**
     * 즐겨찾기 한 건을 만듭니다.
     *
     * created_at 은 JPA Auditing 이 채우는 값이라 저장 없이는 비어 있습니다.
     * 카드의 createdAt 이 그 값에서 오므로 검사에서는 직접 넣어 줍니다.
     */
    private Favorite favorite(UUID placeId, String memo, LocalDateTime createdAt) {
        Favorite favorite = Favorite.create(ACCOUNT_ID, placeId, memo);
        setField(favorite, "createdAt", createdAt);
        return favorite;
    }

    /**
     * 리플렉션으로 값을 넣습니다.
     *
     * 검사를 위해 엔티티에 setter 를 여는 것보다 낫습니다.
     * 그 setter 는 운영 코드에서 아무도 쓰지 않으면서 아무나 값을 바꿀 수 있게 만듭니다.
     *
     * 상위 클래스까지 거슬러 올라가는 것은 createdAt 이 BaseEntity 에 있기 때문입니다.
     */
    private void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(name + " 을 넣지 못했습니다", e);
            }
        }
        throw new IllegalStateException(name + " 필드를 찾지 못했습니다");
    }
}
