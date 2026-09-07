package com.pawtrail.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 최근에 본 장소의 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 Redis 도 띄우지 않습니다.
 * 목록을 어떻게 담고 자르는지는 저장소의 몫이고,
 * 여기서 보는 것은 받아 온 목록을 카드로 어떻게 맞추는가입니다.
 *
 * 즐겨찾기와 방문 기록의 조립과 닮았지만 다른 자리가 하나 있습니다.
 * 즐겨찾기 목록은 그 표에서 나와 하트 상태를 물을 이유가 없었지만,
 * 이 목록은 다른 곳에서 나오므로 어느 것을 담아뒀는지 따로 확인해야 합니다.
 */
@ExtendWith(MockitoExtension.class)
class RecentPlaceServiceTest {

    @Mock
    private RecentPlaceStore recentPlaceStore;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PlaceProvider placeProvider;

    @Mock
    private VerdictProvider verdictProvider;

    @Mock
    private ReviewProvider reviewProvider;

    @InjectMocks
    private RecentPlaceService recentPlaceService;

    private static final UUID ACCOUNT_ID = UUID.fromString("01a0726a-64e9-7712-9ecb-4996e2dcd75f");
    private static final UUID PET_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");

    // ── 기록 ─────────────────────────────────────────────────

    @Test
    @DisplayName("장소를 그대로 저장소에 넘긴다")
    void 기록() {
        recentPlaceService.record(ACCOUNT_ID, PLACE_A);

        verify(recentPlaceStore).push(ACCOUNT_ID, PLACE_A);
    }

    @Test
    @DisplayName("기록할 때 장소가 있는지 확인하지 않는다")
    void 기록은_장소를_안_봄() {
        recentPlaceService.record(ACCOUNT_ID, PLACE_A);

        // 담은 뒤에 사라지는 경우를 어차피 못 막아 거르는 자리를 조회 한 곳에 모았음
        verify(placeProvider, never()).findByIds(anyCollection());
    }

    // ── 목록 ─────────────────────────────────────────────────

    @Test
    @DisplayName("네 곳에서 온 값이 카드에 그대로 담긴다")
    void 카드_조립() {
        givenRecent(PLACE_A);
        givenDefaultPet(PET_ID);
        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, new PlaceData(PLACE_A, "멍멍 카페", "CAFE", "https://img/a.jpg",
                        null, null, false)));
        when(verdictProvider.findByPlaceIds(anyCollection(), eq(PET_ID))).thenReturn(Map.of(
                PLACE_A, new VerdictData("ALLOWED", List.of("목줄 착용"))));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of(PLACE_A, 4.8));
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of(PLACE_A));

        RecentPlaceCardOutput card = recentPlaceService.getRecent(ACCOUNT_ID, 10).get(0);

        assertThat(card.placeId()).isEqualTo(PLACE_A);
        assertThat(card.name()).isEqualTo("멍멍 카페");
        assertThat(card.placeType()).isEqualTo("CAFE");
        assertThat(card.imageUrl()).isEqualTo("https://img/a.jpg");
        assertThat(card.verdict()).isEqualTo("ALLOWED");
        assertThat(card.requiredItems()).containsExactly("목줄 착용");
        assertThat(card.ratingAvg()).isEqualTo(4.8);
        assertThat(card.isFavorite()).isTrue();
    }

    @Test
    @DisplayName("담아 둔 것이 없으면 외부를 한 번도 부르지 않는다")
    void 빈_목록() {
        when(recentPlaceStore.findRecent(any(), anyInt())).thenReturn(List.of());

        assertThat(recentPlaceService.getRecent(ACCOUNT_ID, 10)).isEmpty();

        verify(placeProvider, never()).findByIds(anyCollection());
        verify(verdictProvider, never()).findByPlaceIds(anyCollection(), any());
        verify(reviewProvider, never()).findRatingsByPlaceIds(anyCollection());
    }

    @Test
    @DisplayName("장소를 못 불러오면 목록 전체가 실패한다")
    void place_실패() {
        givenRecent(PLACE_A);
        when(placeProvider.findByIds(anyCollection())).thenReturn(null);

        assertThatThrownBy(() -> recentPlaceService.getRecent(ACCOUNT_ID, 10))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("장소가 일부만 오면 그 카드만 빠지고 나머지는 남는다")
    void place_일부_누락() {
        givenRecent(PLACE_A, PLACE_B);
        givenDefaultPet(null);
        when(placeProvider.findByIds(anyCollection()))
                .thenReturn(Map.of(PLACE_B, place(PLACE_B, "남은 장소", "PARK")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());

        assertThat(recentPlaceService.getRecent(ACCOUNT_ID, 10))
                .extracting(RecentPlaceCardOutput::placeId)
                .containsExactly(PLACE_B);
    }

    @Test
    @DisplayName("없는 장소를 저장소에서 지우지 않는다")
    void 없는_장소를_안_지움() {
        givenRecent(PLACE_A, PLACE_B);
        givenDefaultPet(null);
        when(placeProvider.findByIds(anyCollection()))
                .thenReturn(Map.of(PLACE_B, place(PLACE_B, "남은 장소", "PARK")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());

        recentPlaceService.getRecent(ACCOUNT_ID, 10);

        // 조회가 실패해 일부만 온 경우와 장소가 정말 사라진 경우를 구분할 수 없음
        // 그것을 근거로 지우면 멀쩡한 이력이 사라짐
        verify(recentPlaceStore, never()).deleteAll(any());
    }

    @Test
    @DisplayName("저장소가 준 순서를 서비스가 바꾸지 않는다")
    void 순서_보존() {
        givenRecent(PLACE_A, PLACE_B);
        givenDefaultPet(null);
        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, place(PLACE_A, "먼저", "PARK"),
                PLACE_B, place(PLACE_B, "나중", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());

        assertThat(recentPlaceService.getRecent(ACCOUNT_ID, 10))
                .extracting(RecentPlaceCardOutput::placeId)
                .containsExactly(PLACE_A, PLACE_B);
    }

    // ── 판정 ─────────────────────────────────────────────────

    @Test
    @DisplayName("대표 반려동물이 없으면 판정을 부르지 않고 알 수 없음으로 남긴다")
    void 대표_펫_없음() {
        givenRecent(PLACE_A);
        givenDefaultPet(null);
        when(placeProvider.findByIds(anyCollection()))
                .thenReturn(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());

        RecentPlaceCardOutput card = recentPlaceService.getRecent(ACCOUNT_ID, 10).get(0);

        assertThat(card.verdict()).isEqualTo("UNKNOWN");
        assertThat(card.requiredItems()).isEmpty();
        verify(verdictProvider, never()).findByPlaceIds(anyCollection(), any());
    }

    @Test
    @DisplayName("판정을 못 불러오면 배지가 비고 알 수 없음과 구분된다")
    void 판정_실패() {
        givenRecent(PLACE_A);
        givenDefaultPet(PET_ID);
        when(placeProvider.findByIds(anyCollection()))
                .thenReturn(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(verdictProvider.findByPlaceIds(anyCollection(), eq(PET_ID))).thenReturn(Map.of());
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());

        RecentPlaceCardOutput card = recentPlaceService.getRecent(ACCOUNT_ID, 10).get(0);

        // 대표가 없어 못 부른 것과 불렀는데 실패한 것을 화면이 가려 안내해야 함
        assertThat(card.verdict()).isNull();
        assertThat(card.requiredItems()).isEmpty();
    }

    @Test
    @DisplayName("담아 두지 않은 장소는 하트가 비어 있다")
    void 하트_상태() {
        givenRecent(PLACE_A, PLACE_B);
        givenDefaultPet(null);
        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, place(PLACE_A, "담아 둠", "PARK"),
                PLACE_B, place(PLACE_B, "안 담음", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of(PLACE_A));

        List<RecentPlaceCardOutput> cards = recentPlaceService.getRecent(ACCOUNT_ID, 10);

        assertThat(cards).extracting(RecentPlaceCardOutput::isFavorite)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("요청한 개수를 저장소에 그대로 넘긴다")
    void 개수_전달() {
        when(recentPlaceStore.findRecent(ACCOUNT_ID, 5)).thenReturn(List.of());

        recentPlaceService.getRecent(ACCOUNT_ID, 5);

        verify(recentPlaceStore).findRecent(ACCOUNT_ID, 5);
    }

    // ── 아래는 준비를 돕는 것들임 ─────────────────────────────

    private void givenRecent(UUID... placeIds) {
        when(recentPlaceStore.findRecent(any(), anyInt())).thenReturn(List.of(placeIds));
    }

    private void givenDefaultPet(UUID petId) {
        UserProfile profile = UserProfile.create(ACCOUNT_ID, "다정이네");
        setField(profile, "defaultPetId", petId);
        when(userProfileRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(profile));
    }

    private PlaceData place(UUID placeId, String name, String placeType) {
        return new PlaceData(placeId, name, placeType, null, null, null, false);
    }

    /**
     * 리플렉션으로 값을 넣습니다.
     *
     * 검사를 위해 엔티티에 setter 를 여는 것보다 낫습니다.
     * 그 setter 는 운영 코드에서 아무도 쓰지 않으면서 아무나 값을 바꿀 수 있게 만듭니다.
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
