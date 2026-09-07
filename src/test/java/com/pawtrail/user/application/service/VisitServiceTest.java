package com.pawtrail.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 방문 기록의 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 띄우지 않습니다.
 * 여기서 지키려는 것이 "값을 어디서 가져와 어떻게 맞추는가" 이고
 * 그 판단에 데이터베이스가 관여하지 않기 때문입니다.
 *
 * 개발 중에는 로컬 스텁 서버로 눈으로 확인하지만 그 스텁은 실제 서비스가 생기면 버립니다.
 * 일정도 같은 카드 컴포넌트를 쓰기로 되어 있어 뒤 이슈에서 이 조립 코드를 건드리게 되는데,
 * 그때 어긋난 것을 잡아 줄 자리가 여기입니다.
 *
 * 특히 요약이 그날 첫 행에만 붙는 규칙은 같은 날 방문이 둘 있어야 드러납니다.
 * 실물 검증에서는 그 조합을 만들지 않았습니다.
 */
@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    @Mock
    private VisitLogRepository visitLogRepository;

    @Mock
    private ItineraryStopRepository itineraryStopRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private DailySummaryRepository dailySummaryRepository;

    @Mock
    private PlaceProvider placeProvider;

    @Mock
    private VerdictProvider verdictProvider;

    @Mock
    private ReviewProvider reviewProvider;

    @InjectMocks
    private VisitService visitService;

    private static final UUID ACCOUNT_ID = UUID.fromString("01a0726a-64e9-7712-9ecb-4996e2dcd75f");
    private static final UUID PET_A = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID PET_B = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");
    private static final UUID STOP_ID = UUID.fromString("cccccccc-0000-7000-8000-000000000003");
    private static final UUID VISIT_ID = UUID.fromString("dddddddd-0000-7000-8000-000000000004");

    // ── 기록하기 ──────────────────────────────────────────────

    @Test
    @DisplayName("일정에서 온 방문은 일정 행의 값이 요청값을 이긴다")
    void 일정이_이김() {
        LocalDateTime stopVisitAt = LocalDateTime.of(2026, 9, 3, 14, 0);
        ItineraryStop stop = itineraryStop(PLACE_A, stopVisitAt, PET_A);

        when(itineraryStopRepository.findByIdAndAccountId(STOP_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));
        when(visitLogRepository.findByItineraryStopId(STOP_ID)).thenReturn(Optional.empty());
        when(verdictProvider.findByPlaceIds(List.of(PLACE_A), PET_A))
                .thenReturn(Map.of(PLACE_A, new VerdictData("ALLOWED", List.of("목줄 착용"))));
        when(visitLogRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // 요청은 일부러 전부 다른 값을 보냄
        visitService.record(ACCOUNT_ID, new VisitCreateInput(
                PLACE_B, STOP_ID, LocalDateTime.of(2026, 9, 30, 23, 59), PET_B, "메모"));

        ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
        verify(visitLogRepository).save(captor.capture());
        VisitLog saved = captor.getValue();

        assertThat(saved.getPlaceId()).isEqualTo(PLACE_A);
        assertThat(saved.getVisitedAt()).isEqualTo(stopVisitAt);
        assertThat(saved.getPetId()).isEqualTo(PET_A);
        // 메모는 일정에서 읽지 않으므로 요청값이 그대로 들어감
        assertThat(saved.getMemo()).isEqualTo("메모");
    }

    @Test
    @DisplayName("이미 기록한 일정을 다시 누르면 기존 식별자가 나가고 저장하지 않는다")
    void 중복_기록() {
        VisitLog already = visitLog(PLACE_A, LocalDateTime.now(), PET_A, Verdict.ALLOWED);
        setField(already, "id", VISIT_ID);

        when(itineraryStopRepository.findByIdAndAccountId(STOP_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(itineraryStop(PLACE_A, LocalDateTime.now(), PET_A)));
        when(visitLogRepository.findByItineraryStopId(STOP_ID)).thenReturn(Optional.of(already));

        VisitCreateOutput output = visitService.record(
                ACCOUNT_ID, new VisitCreateInput(null, STOP_ID, null, null, null));

        assertThat(output.visitId()).isEqualTo(VISIT_ID);
        verify(visitLogRepository, never()).save(any());
        // 판정은 부르지 않아야 함, 새로 만들 것이 없으므로
        verify(verdictProvider, never()).findByPlaceIds(anyCollection(), any());
    }

    @Test
    @DisplayName("남의 일정에 이미 기록이 있어도 그 식별자가 새어 나가지 않는다")
    void 남의_일정에_기록이_있어도() {
        VisitLog othersVisit = visitLog(PLACE_A, LocalDateTime.now(), PET_A, Verdict.ALLOWED);
        setField(othersVisit, "id", VISIT_ID);

        // 그 일정은 내 것이 아님
        when(itineraryStopRepository.findByIdAndAccountId(STOP_ID, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitService.record(
                ACCOUNT_ID, new VisitCreateInput(null, STOP_ID, null, null, null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);

        // 소유권 검증이 먼저라 중복 조회까지 가지 않아야 함
        // 순서가 뒤집히면 남의 visitId 가 응답에 실려 나감
        verify(visitLogRepository, never()).findByItineraryStopId(any());
    }

    @Test
    @DisplayName("내 것이 아닌 일정으로 기록하려 하면 실패한다")
    void 남의_일정() {
        when(itineraryStopRepository.findByIdAndAccountId(STOP_ID, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitService.record(
                ACCOUNT_ID, new VisitCreateInput(null, STOP_ID, null, null, null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);

        verify(visitLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("판정을 못 불러오면 기록을 만들지 않고 실패한다")
    void 판정_실패() {
        when(verdictProvider.findByPlaceIds(List.of(PLACE_A), PET_A)).thenReturn(Map.of());

        assertThatThrownBy(() -> visitService.record(ACCOUNT_ID, new VisitCreateInput(
                PLACE_A, null, LocalDateTime.of(2026, 9, 6, 10, 0), PET_A, null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.VERDICT_UNAVAILABLE);

        verify(visitLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("반려동물이 없으면 판정을 부르지 않고 UNKNOWN 으로 남긴다")
    void 펫_없음() {
        when(visitLogRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        visitService.record(ACCOUNT_ID, new VisitCreateInput(
                PLACE_A, null, LocalDateTime.of(2026, 9, 6, 10, 0), null, null));

        ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
        verify(visitLogRepository).save(captor.capture());

        assertThat(captor.getValue().getVerdictAtVisit()).isEqualTo(Verdict.UNKNOWN);
        verify(verdictProvider, never()).findByPlaceIds(anyCollection(), any());
    }

    // ── 목록 ─────────────────────────────────────────────────

    @Test
    @DisplayName("다섯 곳에서 온 값이 카드에 그대로 담긴다")
    void 카드_조립() {
        LocalDateTime visitedAt = LocalDateTime.of(2026, 9, 7, 11, 0);
        VisitLog visit = visitLog(PLACE_A, visitedAt, PET_A, Verdict.ALLOWED);
        setField(visit, "id", VISIT_ID);
        setField(visit, "memo", "메모");

        givenVisits(visit);
        givenPlaces(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(verdictProvider.findByPlaceIdsForPets(anyCollection(), anyCollection()))
                .thenReturn(Map.of(PLACE_A, Map.of(
                        PET_A, new VerdictData("ALLOWED", List.of("목줄 착용", "배변봉투 지참")))));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of(PLACE_A, 4.8));
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of(PLACE_A));
        givenSummaries();

        List<VisitCardOutput> cards = visitService.getMyVisits(ACCOUNT_ID);

        assertThat(cards).hasSize(1);
        VisitCardOutput card = cards.get(0);
        assertThat(card.visitId()).isEqualTo(VISIT_ID);
        assertThat(card.placeId()).isEqualTo(PLACE_A);
        assertThat(card.name()).isEqualTo("멍멍 카페");
        assertThat(card.placeType()).isEqualTo("CAFE");
        assertThat(card.requiredItems()).containsExactly("목줄 착용", "배변봉투 지참");
        assertThat(card.ratingAvg()).isEqualTo(4.8);
        assertThat(card.visitedAt()).isEqualTo(visitedAt);
        assertThat(card.petId()).isEqualTo(PET_A);
        assertThat(card.verdictAtVisit()).isEqualTo("ALLOWED");
        assertThat(card.memo()).isEqualTo("메모");
        assertThat(card.isFavorite()).isTrue();
    }

    @Test
    @DisplayName("담아 둔 것이 없으면 외부를 한 번도 부르지 않는다")
    void 빈_목록() {
        when(visitLogRepository.findAllByAccountIdOrderByVisitedAt(ACCOUNT_ID))
                .thenReturn(List.of());

        assertThat(visitService.getMyVisits(ACCOUNT_ID)).isEmpty();

        verify(placeProvider, never()).findByIds(anyCollection());
        verify(verdictProvider, never()).findByPlaceIdsForPets(anyCollection(), anyCollection());
        verify(reviewProvider, never()).findRatingsByPlaceIds(anyCollection());
    }

    @Test
    @DisplayName("장소를 못 불러오면 목록 전체가 실패한다")
    void place_실패() {
        givenVisits(visitLog(PLACE_A, LocalDateTime.now(), PET_A, Verdict.ALLOWED));
        when(placeProvider.findByIds(anyCollection())).thenReturn(null);

        assertThatThrownBy(() -> visitService.getMyVisits(ACCOUNT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("장소가 일부만 오면 그 카드만 빠지고 나머지는 남는다")
    void place_일부_누락() {
        givenVisits(
                visitLog(PLACE_A, LocalDateTime.of(2026, 9, 7, 11, 0), null, Verdict.UNKNOWN),
                visitLog(PLACE_B, LocalDateTime.of(2026, 9, 6, 11, 0), null, Verdict.UNKNOWN));
        givenPlaces(Map.of(PLACE_B, place(PLACE_B, "남은 장소", "PARK")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());
        givenSummaries();

        List<VisitCardOutput> cards = visitService.getMyVisits(ACCOUNT_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).placeId()).isEqualTo(PLACE_B);
    }

    @Test
    @DisplayName("판정을 못 불러와도 배지는 저장된 값이고 준비물만 빈다")
    void 목록_판정_실패() {
        givenVisits(visitLog(PLACE_A, LocalDateTime.now(), PET_A, Verdict.NOT_ALLOWED));
        givenPlaces(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(verdictProvider.findByPlaceIdsForPets(anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of(PLACE_A, 4.8));
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());
        givenSummaries();

        List<VisitCardOutput> cards = visitService.getMyVisits(ACCOUNT_ID);

        assertThat(cards.get(0).requiredItems()).isEmpty();
        // 배지는 저장된 값이라 영향을 받지 않아야 함
        assertThat(cards.get(0).verdictAtVisit()).isEqualTo("NOT_ALLOWED");
        assertThat(cards.get(0).ratingAvg()).isEqualTo(4.8);
    }

    @Test
    @DisplayName("준비물은 방문마다 그 방문의 반려동물 기준으로 고른다")
    void 방문마다_다른_펫() {
        givenVisits(
                visitLog(PLACE_A, LocalDateTime.of(2026, 9, 7, 11, 0), PET_A, Verdict.ALLOWED),
                visitLog(PLACE_A, LocalDateTime.of(2026, 9, 6, 11, 0), PET_B, Verdict.NOT_ALLOWED));
        givenPlaces(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(verdictProvider.findByPlaceIdsForPets(anyCollection(), anyCollection()))
                .thenReturn(Map.of(PLACE_A, Map.of(
                        PET_A, new VerdictData("ALLOWED", List.of("목줄 착용")),
                        PET_B, new VerdictData("NOT_ALLOWED", List.of()))));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());
        givenSummaries();

        List<VisitCardOutput> cards = visitService.getMyVisits(ACCOUNT_ID);

        assertThat(cards.get(0).requiredItems()).containsExactly("목줄 착용");
        assertThat(cards.get(1).requiredItems()).isEmpty();
    }

    @Test
    @DisplayName("요약은 그날 첫 행에만 붙고 같은 날 나머지는 비어 있다")
    void 요약은_그날_첫_행에만() {
        LocalDate day = LocalDate.of(2026, 9, 5);
        givenVisits(
                visitLog(PLACE_A, day.atTime(11, 0), null, Verdict.UNKNOWN),
                visitLog(PLACE_B, day.atTime(15, 0), null, Verdict.UNKNOWN));
        givenPlaces(Map.of(
                PLACE_A, place(PLACE_A, "첫 곳", "PARK"),
                PLACE_B, place(PLACE_B, "둘째 곳", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());
        givenSummaries(DailySummary.create(
                ACCOUNT_ID, day, "카페와 공원을 다녀온 하루였어요.", LocalDateTime.now()));

        List<VisitCardOutput> cards = visitService.getMyVisits(ACCOUNT_ID);

        assertThat(cards.get(0).summary()).isEqualTo("카페와 공원을 다녀온 하루였어요.");
        assertThat(cards.get(1).summary()).isNull();
    }

    @Test
    @DisplayName("리포지터리가 준 순서를 서비스가 바꾸지 않는다")
    void 순서_보존() {
        givenVisits(
                visitLog(PLACE_A, LocalDateTime.of(2026, 9, 7, 11, 0), null, Verdict.UNKNOWN),
                visitLog(PLACE_B, LocalDateTime.of(2026, 9, 5, 11, 0), null, Verdict.UNKNOWN));
        givenPlaces(Map.of(
                PLACE_A, place(PLACE_A, "먼저", "PARK"),
                PLACE_B, place(PLACE_B, "나중", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(favoriteRepository.findPlaceIdsByAccountIdAndPlaceIdIn(any(), anyCollection()))
                .thenReturn(Set.of());
        givenSummaries();

        assertThat(visitService.getMyVisits(ACCOUNT_ID))
                .extracting(VisitCardOutput::placeId)
                .containsExactly(PLACE_A, PLACE_B);
    }

    // ── 삭제 ─────────────────────────────────────────────────

    @Test
    @DisplayName("없거나 내 것이 아닌 기록을 지우려 하면 실패한다")
    void 삭제_없음() {
        when(visitLogRepository.findByIdAndAccountId(VISIT_ID, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitService.remove(ACCOUNT_ID, VISIT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.VISIT_NOT_FOUND);

        verify(visitLogRepository, never()).delete(any());
    }

    // ── 아래는 준비를 돕는 것들임 ─────────────────────────────

    private void givenVisits(VisitLog... visits) {
        when(visitLogRepository.findAllByAccountIdOrderByVisitedAt(ACCOUNT_ID))
                .thenReturn(List.of(visits));
    }

    private void givenPlaces(Map<UUID, PlaceData> places) {
        when(placeProvider.findByIds(anyCollection())).thenReturn(places);
    }

    private void givenSummaries(DailySummary... summaries) {
        when(dailySummaryRepository.findByAccountIdAndVisitDateIn(any(), anyCollection()))
                .thenReturn(List.of(summaries));
    }

    private PlaceData place(UUID placeId, String name, String placeType) {
        return new PlaceData(placeId, name, placeType, null, null, null);
    }

    private ItineraryStop itineraryStop(UUID placeId, LocalDateTime visitAt, UUID petId) {
        return ItineraryStop.create(ACCOUNT_ID, placeId, visitAt, petId, 1, null);
    }

    private VisitLog visitLog(UUID placeId, LocalDateTime visitedAt, UUID petId, Verdict verdict) {
        return VisitLog.create(ACCOUNT_ID, placeId, petId, visitedAt, verdict, null, null);
    }

    /**
     * 리플렉션으로 값을 넣습니다.
     *
     * 검사를 위해 엔티티에 setter 를 여는 것보다 낫습니다.
     * 그 setter 는 운영 코드에서 아무도 쓰지 않으면서 아무나 값을 바꿀 수 있게 만듭니다.
     *
     * 식별자는 저장할 때 만들어지므로 검사에서는 직접 넣어 줍니다.
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
