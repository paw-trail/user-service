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
import com.pawtrail.user.application.dto.input.ItineraryCreateInput;
import com.pawtrail.user.application.dto.input.ItineraryUpdateInput;
import com.pawtrail.user.application.dto.output.ItineraryCardOutput;
import com.pawtrail.user.application.dto.output.ItineraryCreateOutput;
import com.pawtrail.user.domain.enums.Verdict;
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
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 일정의 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 띄우지 않습니다.
 * 즐겨찾기와 방문 기록이 같은 틀을 쓰고 있습니다.
 *
 * 실물 검증으로는 드러나지 않는 자리가 몇 있습니다.
 *
 *   날짜 경계    다음 날 00:00 짜리 일정이 오늘 목록에 끼지 않는가.
 *               스텁으로 보려면 그 조합을 일부러 만들어야 함
 *   판정 두 값   동반 동물이 없어 UNKNOWN 인 것과 호출이 실패해 null 인 것이 갈리는가
 *   연쇄 삭제    일정을 지울 때 방문 기록도 함께 지우는가
 *   날짜 변경    같은 날 안에서만 시각을 고칠 수 있는가
 */
@ExtendWith(MockitoExtension.class)
class ItineraryServiceTest {

    @Mock
    private ItineraryStopRepository itineraryStopRepository;

    @Mock
    private VisitLogRepository visitLogRepository;

    @Mock
    private PlaceProvider placeProvider;

    @Mock
    private VerdictProvider verdictProvider;

    @Mock
    private ReviewProvider reviewProvider;

    @InjectMocks
    private ItineraryService itineraryService;

    private static final UUID ACCOUNT_ID = UUID.fromString("01a0726a-64e9-7712-9ecb-4996e2dcd75f");
    private static final UUID PET_A = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID PET_B = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");
    private static final UUID STOP_A = UUID.fromString("cccccccc-0000-7000-8000-000000000003");
    private static final UUID STOP_B = UUID.fromString("cccccccc-0000-7000-8000-000000000004");
    private static final UUID VISIT_ID = UUID.fromString("dddddddd-0000-7000-8000-000000000005");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 9, 1, 11, 0);
    private static final LocalDateTime AFTERNOON = LocalDateTime.of(2026, 9, 1, 15, 0);

    // ── 담기 ─────────────────────────────────────────────────

    @Test
    @DisplayName("그날이 비어 있으면 첫 순서가 1 이 된다")
    void 첫_순서() {
        when(itineraryStopRepository.findByAccountIdAndPlaceIdAndVisitAt(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(itineraryStopRepository.findMaxVisitOrder(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(0);
        when(itineraryStopRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        itineraryService.add(ACCOUNT_ID, new ItineraryCreateInput(PLACE_A, MORNING, PET_A, null));

        assertThat(savedStop().getVisitOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 담긴 것이 있으면 그날 마지막 순서에 하나를 더한다")
    void 마지막_다음_순서() {
        when(itineraryStopRepository.findByAccountIdAndPlaceIdAndVisitAt(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(itineraryStopRepository.findMaxVisitOrder(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(4);
        when(itineraryStopRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        itineraryService.add(ACCOUNT_ID, new ItineraryCreateInput(PLACE_A, MORNING, PET_A, null));

        assertThat(savedStop().getVisitOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("마지막 순서는 그날 하루만 보고 다음 날 00:00 은 빼고 센다")
    void 순서_조회_경계() {
        when(itineraryStopRepository.findByAccountIdAndPlaceIdAndVisitAt(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(itineraryStopRepository.findMaxVisitOrder(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(0);
        when(itineraryStopRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        itineraryService.add(ACCOUNT_ID, new ItineraryCreateInput(PLACE_A, AFTERNOON, PET_A, null));

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(itineraryStopRepository)
                .findMaxVisitOrder(eq(ACCOUNT_ID), start.capture(), end.capture());

        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 2, 0, 0));
    }

    @Test
    @DisplayName("같은 시각에 같은 장소를 또 담으면 기존 식별자가 나가고 저장하지 않는다")
    void 중복_담기() {
        ItineraryStop existing = stop(PLACE_A, MORNING, PET_A, 1, STOP_A);
        when(itineraryStopRepository.findByAccountIdAndPlaceIdAndVisitAt(ACCOUNT_ID, PLACE_A, MORNING))
                .thenReturn(Optional.of(existing));

        ItineraryCreateOutput output = itineraryService.add(
                ACCOUNT_ID, new ItineraryCreateInput(PLACE_A, MORNING, PET_B, "다른 메모"));

        assertThat(output.stopId()).isEqualTo(STOP_A);
        verify(itineraryStopRepository, never()).save(any());
        verify(itineraryStopRepository, never()).findMaxVisitOrder(any(), any(), any());
    }

    @Test
    @DisplayName("같은 장소라도 시각이 다르면 새로 담긴다")
    void 같은_장소_다른_시각() {
        when(itineraryStopRepository.findByAccountIdAndPlaceIdAndVisitAt(ACCOUNT_ID, PLACE_A, AFTERNOON))
                .thenReturn(Optional.empty());
        when(itineraryStopRepository.findMaxVisitOrder(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(1);
        when(itineraryStopRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        itineraryService.add(ACCOUNT_ID, new ItineraryCreateInput(PLACE_A, AFTERNOON, PET_A, null));

        assertThat(savedStop().getVisitAt()).isEqualTo(AFTERNOON);
        assertThat(savedStop().getVisitOrder()).isEqualTo(2);
    }

    // ── 목록 ─────────────────────────────────────────────────

    @Test
    @DisplayName("네 곳에서 온 값이 카드에 그대로 담긴다")
    void 카드_조립() {
        givenStops(stop(PLACE_A, MORNING, PET_A, 1, STOP_A));
        givenPlaces(Map.of(PLACE_A, new PlaceData(PLACE_A, "멍멍 카페", "CAFE", "https://img/a.jpg",
                new BigDecimal("37.5665"), new BigDecimal("126.9780"), true)));
        when(verdictProvider.findByPlaceIdsForPets(anyCollection(), anyCollection()))
                .thenReturn(Map.of(PLACE_A, Map.of(PET_A,
                        new VerdictData("ALLOWED", List.of("목줄 착용", "배변봉투 지참")))));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of(PLACE_A, 4.8));
        when(visitLogRepository.findByItineraryStopId(STOP_A)).thenReturn(Optional.empty());

        ItineraryCardOutput card = itineraryService.getMyItinerary(ACCOUNT_ID, DAY).get(0);

        assertThat(card.stopId()).isEqualTo(STOP_A);
        assertThat(card.placeId()).isEqualTo(PLACE_A);
        assertThat(card.name()).isEqualTo("멍멍 카페");
        assertThat(card.placeType()).isEqualTo("CAFE");
        assertThat(card.imageUrl()).isEqualTo("https://img/a.jpg");
        assertThat(card.lat()).isEqualByComparingTo("37.5665");
        assertThat(card.lon()).isEqualByComparingTo("126.9780");
        assertThat(card.supplyPoint()).isTrue();
        assertThat(card.visitAt()).isEqualTo(MORNING);
        assertThat(card.petId()).isEqualTo(PET_A);
        assertThat(card.visitOrder()).isEqualTo(1);
        assertThat(card.verdict()).isEqualTo("ALLOWED");
        assertThat(card.requiredItems()).containsExactly("목줄 착용", "배변봉투 지참");
        assertThat(card.ratingAvg()).isEqualTo(4.8);
        assertThat(card.visited()).isFalse();
        assertThat(card.visitId()).isNull();
    }

    @Test
    @DisplayName("목록은 그날 하루만 조회하며 다음 날 00:00 을 빼고 묻는다")
    void 목록_조회_경계() {
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of());

        itineraryService.getMyItinerary(ACCOUNT_ID, DAY);

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(itineraryStopRepository)
                .findAllByAccountIdAndDay(eq(ACCOUNT_ID), start.capture(), end.capture());

        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 2, 0, 0));
    }

    @Test
    @DisplayName("담아 둔 것이 없으면 외부를 한 번도 부르지 않는다")
    void 빈_목록() {
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of());

        assertThat(itineraryService.getMyItinerary(ACCOUNT_ID, DAY)).isEmpty();

        verify(placeProvider, never()).findByIds(anyCollection());
        verify(verdictProvider, never()).findByPlaceIdsForPets(anyCollection(), anyCollection());
        verify(reviewProvider, never()).findRatingsByPlaceIds(anyCollection());
    }

    @Test
    @DisplayName("장소를 못 불러오면 목록 전체가 실패한다")
    void place_실패() {
        givenStops(stop(PLACE_A, MORNING, PET_A, 1, STOP_A));
        givenPlaces(null);

        assertThatThrownBy(() -> itineraryService.getMyItinerary(ACCOUNT_ID, DAY))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("장소가 일부만 오면 그 카드만 빠지고 나머지는 남는다")
    void place_일부_누락() {
        givenStops(stop(PLACE_A, MORNING, PET_A, 1, STOP_A),
                stop(PLACE_B, AFTERNOON, PET_A, 2, STOP_B));
        givenPlaces(Map.of(PLACE_B, place(PLACE_B, "남은 장소", "PARK")));
        when(verdictProvider.findByPlaceIdsForPets(anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(visitLogRepository.findByItineraryStopId(any())).thenReturn(Optional.empty());

        assertThat(itineraryService.getMyItinerary(ACCOUNT_ID, DAY))
                .extracting(ItineraryCardOutput::placeId)
                .containsExactly(PLACE_B);
    }

    @Test
    @DisplayName("판정을 못 불러오면 배지가 null 이고 준비물은 빈 목록이다")
    void 판정_실패() {
        givenStops(stop(PLACE_A, MORNING, PET_A, 1, STOP_A));
        givenPlaces(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(verdictProvider.findByPlaceIdsForPets(anyCollection(), anyCollection()))
                .thenReturn(Map.of());
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(visitLogRepository.findByItineraryStopId(any())).thenReturn(Optional.empty());

        ItineraryCardOutput card = itineraryService.getMyItinerary(ACCOUNT_ID, DAY).get(0);

        assertThat(card.verdict()).isNull();
        assertThat(card.requiredItems()).isEmpty();
    }

    @Test
    @DisplayName("동반 동물이 없으면 판정을 부르지 않고 UNKNOWN 으로 남긴다")
    void 펫_없음() {
        givenStops(stop(PLACE_A, MORNING, null, 1, STOP_A));
        givenPlaces(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(visitLogRepository.findByItineraryStopId(any())).thenReturn(Optional.empty());

        ItineraryCardOutput card = itineraryService.getMyItinerary(ACCOUNT_ID, DAY).get(0);

        assertThat(card.verdict()).isEqualTo("UNKNOWN");
        assertThat(card.requiredItems()).isEmpty();
        verify(verdictProvider, never()).findByPlaceIdsForPets(anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("판정은 일정마다 그 일정의 반려동물 기준으로 고른다")
    void 일정마다_다른_펫() {
        givenStops(stop(PLACE_A, MORNING, PET_A, 1, STOP_A),
                stop(PLACE_A, AFTERNOON, PET_B, 2, STOP_B));
        givenPlaces(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(verdictProvider.findByPlaceIdsForPets(anyCollection(), anyCollection()))
                .thenReturn(Map.of(PLACE_A, Map.of(
                        PET_A, new VerdictData("ALLOWED", List.of("목줄 착용")),
                        PET_B, new VerdictData("NOT_ALLOWED", List.of("이동장")))));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(visitLogRepository.findByItineraryStopId(any())).thenReturn(Optional.empty());

        List<ItineraryCardOutput> cards = itineraryService.getMyItinerary(ACCOUNT_ID, DAY);

        assertThat(cards).extracting(ItineraryCardOutput::verdict)
                .containsExactly("ALLOWED", "NOT_ALLOWED");
        assertThat(cards.get(0).requiredItems()).containsExactly("목줄 착용");
        assertThat(cards.get(1).requiredItems()).containsExactly("이동장");
    }

    @Test
    @DisplayName("이미 다녀온 일정은 방문 여부와 그 식별자가 함께 실린다")
    void 방문_여부() {
        givenStops(stop(PLACE_A, MORNING, null, 1, STOP_A));
        givenPlaces(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(visitLogRepository.findByItineraryStopId(STOP_A))
                .thenReturn(Optional.of(visitLog()));

        ItineraryCardOutput card = itineraryService.getMyItinerary(ACCOUNT_ID, DAY).get(0);

        assertThat(card.visited()).isTrue();
        assertThat(card.visitId()).isEqualTo(VISIT_ID);
    }

    @Test
    @DisplayName("리포지터리가 준 순서를 서비스가 바꾸지 않는다")
    void 순서_보존() {
        givenStops(stop(PLACE_A, MORNING, null, 1, STOP_A),
                stop(PLACE_B, AFTERNOON, null, 2, STOP_B));
        givenPlaces(Map.of(
                PLACE_A, place(PLACE_A, "먼저", "PARK"),
                PLACE_B, place(PLACE_B, "나중", "CAFE")));
        when(reviewProvider.findRatingsByPlaceIds(anyCollection())).thenReturn(Map.of());
        when(visitLogRepository.findByItineraryStopId(any())).thenReturn(Optional.empty());

        assertThat(itineraryService.getMyItinerary(ACCOUNT_ID, DAY))
                .extracting(ItineraryCardOutput::placeId)
                .containsExactly(PLACE_A, PLACE_B);
    }

    // ── 수정 ─────────────────────────────────────────────────

    @Test
    @DisplayName("보내지 않은 항목은 그대로 두고 보낸 것만 바꾼다")
    void 보낸_것만_바꿈() {
        ItineraryStop stop = stop(PLACE_A, MORNING, PET_A, 1, STOP_A);
        setField(stop, "memo", "원래 메모");
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));

        itineraryService.update(ACCOUNT_ID, STOP_A,
                new ItineraryUpdateInput(null, false, null, true, "새 메모"));

        assertThat(stop.getVisitAt()).isEqualTo(MORNING);
        assertThat(stop.getPetId()).isEqualTo(PET_A);
        assertThat(stop.getMemo()).isEqualTo("새 메모");
    }

    @Test
    @DisplayName("동반 동물에 null 을 보내면 뗀다")
    void 동반_동물_해제() {
        ItineraryStop stop = stop(PLACE_A, MORNING, PET_A, 1, STOP_A);
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));

        itineraryService.update(ACCOUNT_ID, STOP_A,
                new ItineraryUpdateInput(null, true, null, false, null));

        assertThat(stop.getPetId()).isNull();
    }

    @Test
    @DisplayName("같은 날 안에서 시각을 고치면 순서는 그대로 둔다")
    void 시각만_고침() {
        ItineraryStop stop = stop(PLACE_A, MORNING, PET_A, 3, STOP_A);
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));
        when(itineraryStopRepository.findByAccountIdAndPlaceIdAndVisitAt(ACCOUNT_ID, PLACE_A, AFTERNOON))
                .thenReturn(Optional.empty());

        itineraryService.update(ACCOUNT_ID, STOP_A,
                new ItineraryUpdateInput(AFTERNOON, false, null, false, null));

        assertThat(stop.getVisitAt()).isEqualTo(AFTERNOON);
        assertThat(stop.getVisitOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 날로 옮기려 하면 실패한다")
    void 날짜_변경_거부() {
        ItineraryStop stop = stop(PLACE_A, MORNING, PET_A, 1, STOP_A);
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));

        LocalDateTime nextDay = LocalDateTime.of(2026, 9, 3, 15, 0);

        assertThatThrownBy(() -> itineraryService.update(ACCOUNT_ID, STOP_A,
                new ItineraryUpdateInput(nextDay, false, null, false, null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED);

        assertThat(stop.getVisitAt()).isEqualTo(MORNING);
    }

    @Test
    @DisplayName("고친 시각에 같은 장소가 이미 담겨 있으면 실패한다")
    void 시각_충돌() {
        ItineraryStop stop = stop(PLACE_A, MORNING, PET_A, 1, STOP_A);
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));
        when(itineraryStopRepository.findByAccountIdAndPlaceIdAndVisitAt(ACCOUNT_ID, PLACE_A, AFTERNOON))
                .thenReturn(Optional.of(stop(PLACE_A, AFTERNOON, PET_A, 2, STOP_B)));

        assertThatThrownBy(() -> itineraryService.update(ACCOUNT_ID, STOP_A,
                new ItineraryUpdateInput(AFTERNOON, false, null, false, null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.ITINERARY_DUPLICATE);

        assertThat(stop.getVisitAt()).isEqualTo(MORNING);
    }

    @Test
    @DisplayName("없거나 내 것이 아닌 일정을 고치려 하면 실패한다")
    void 수정_없음() {
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> itineraryService.update(ACCOUNT_ID, STOP_A,
                new ItineraryUpdateInput(AFTERNOON, false, null, false, null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.ITINERARY_NOT_FOUND);

        verify(itineraryStopRepository, never())
                .findByAccountIdAndPlaceIdAndVisitAt(any(), any(), any());
    }

    // ── 삭제 ─────────────────────────────────────────────────

    @Test
    @DisplayName("일정을 지우면 연결된 방문 기록도 함께 지운다")
    void 연쇄_삭제() {
        ItineraryStop stop = stop(PLACE_A, MORNING, PET_A, 1, STOP_A);
        VisitLog visit = visitLog();
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));
        when(visitLogRepository.findByItineraryStopId(STOP_A)).thenReturn(Optional.of(visit));

        itineraryService.remove(ACCOUNT_ID, STOP_A);

        verify(visitLogRepository).delete(visit);
        verify(itineraryStopRepository).delete(stop);
    }

    @Test
    @DisplayName("다녀오지 않은 일정은 일정만 지운다")
    void 방문_기록_없이_삭제() {
        ItineraryStop stop = stop(PLACE_A, MORNING, PET_A, 1, STOP_A);
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.of(stop));
        when(visitLogRepository.findByItineraryStopId(STOP_A)).thenReturn(Optional.empty());

        itineraryService.remove(ACCOUNT_ID, STOP_A);

        verify(visitLogRepository, never()).delete(any());
        verify(itineraryStopRepository).delete(stop);
    }

    @Test
    @DisplayName("없거나 내 것이 아닌 일정을 지우려 하면 실패한다")
    void 삭제_없음() {
        when(itineraryStopRepository.findByIdAndAccountId(STOP_A, ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> itineraryService.remove(ACCOUNT_ID, STOP_A))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.ITINERARY_NOT_FOUND);

        verify(itineraryStopRepository, never()).delete(any());
        verify(visitLogRepository, never()).delete(any());
    }

    // ── 일정이 있는 날짜 ──────────────────────────────────────

    @Test
    @DisplayName("같은 날에 여럿을 담아도 날짜는 한 번만 나온다")
    void 날짜_중복_접힘() {
        when(itineraryStopRepository.findVisitAtsInRange(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(
                        LocalDateTime.of(2026, 9, 1, 11, 0),
                        LocalDateTime.of(2026, 9, 1, 15, 0),
                        LocalDateTime.of(2026, 9, 7, 9, 30)));

        assertThat(itineraryService.getDates(ACCOUNT_ID, DAY, LocalDate.of(2026, 9, 30)))
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));
    }

    @Test
    @DisplayName("날짜 조회도 끝 날의 다음 날 00:00 을 빼고 묻는다")
    void 날짜_조회_경계() {
        when(itineraryStopRepository.findVisitAtsInRange(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of());

        itineraryService.getDates(ACCOUNT_ID, DAY, LocalDate.of(2026, 9, 30));

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(itineraryStopRepository)
                .findVisitAtsInRange(eq(ACCOUNT_ID), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    @Test
    @DisplayName("시작이 끝보다 늦으면 조회하지 않고 실패한다")
    void 범위_거꾸로() {
        assertThatThrownBy(() -> itineraryService.getDates(
                ACCOUNT_ID, LocalDate.of(2026, 9, 30), DAY))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED);

        verify(itineraryStopRepository, never()).findVisitAtsInRange(any(), any(), any());
    }

    // ── 아래는 준비를 돕는 것들임 ─────────────────────────────

    private void givenStops(ItineraryStop... stops) {
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of(stops));
    }

    private void givenPlaces(Map<UUID, PlaceData> places) {
        when(placeProvider.findByIds(anyCollection())).thenReturn(places);
    }

    private PlaceData place(UUID placeId, String name, String placeType) {
        return new PlaceData(placeId, name, placeType, null, null, null, false);
    }

    private ItineraryStop stop(UUID placeId, LocalDateTime visitAt, UUID petId,
                               int visitOrder, UUID id) {
        ItineraryStop stop =
                ItineraryStop.create(ACCOUNT_ID, placeId, visitAt, petId, visitOrder, null);
        setField(stop, "id", id);
        return stop;
    }

    private VisitLog visitLog() {
        VisitLog visit = VisitLog.create(
                ACCOUNT_ID, PLACE_A, PET_A, MORNING, Verdict.ALLOWED, STOP_A, null);
        setField(visit, "id", VISIT_ID);
        return visit;
    }

    /**
     * 저장하려고 넘긴 일정을 꺼냅니다.
     *
     * 순서를 서버가 채우는 값이라 응답에는 안 실립니다.
     * 그래서 저장 인자를 붙잡아 확인합니다.
     */
    private ItineraryStop savedStop() {
        ArgumentCaptor<ItineraryStop> captor = ArgumentCaptor.forClass(ItineraryStop.class);
        verify(itineraryStopRepository).save(captor.capture());
        return captor.getValue();
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
