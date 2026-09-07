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
import com.pawtrail.user.application.dto.output.DailySummaryOutput;
import com.pawtrail.user.domain.enums.Verdict;
import com.pawtrail.user.domain.exception.UserErrorCode;
import com.pawtrail.user.domain.model.DailySummary;
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
import java.lang.reflect.Field;
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
 * 하루 요약의 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 언어 모델도 띄우지 않습니다.
 * 여기서 지키려는 것이 "언제 부르고 언제 안 부르는가" 와 "무엇을 재료로 넘기는가" 이고,
 * 그 판단에 실제 호출이 관여하지 않습니다.
 *
 * 실물 검증으로는 드러나기 어려운 자리가 몇 있습니다.
 *
 *   검사 순서    한도를 쿨다운보다 먼저 보는가.  순서가 뒤집혀도 결과는 같아 보임
 *   되돌리기     실패했을 때 쿨다운만 돌려주고 한도는 그대로 두는가
 *   재료 가르기   다녀온 곳과 담아만 둔 곳이 다른 목록으로 넘어가는가
 *   갱신         이미 있는 요약을 새 객체로 덮어쓰지 않는가
 */
@ExtendWith(MockitoExtension.class)
class DailySummaryServiceTest {

    @Mock
    private DailySummaryRepository dailySummaryRepository;

    @Mock
    private VisitLogRepository visitLogRepository;

    @Mock
    private ItineraryStopRepository itineraryStopRepository;

    @Mock
    private SummaryRateLimitStore summaryRateLimitStore;

    @Mock
    private PlaceProvider placeProvider;

    @Mock
    private ReviewProvider reviewProvider;

    @Mock
    private LlmProvider llmProvider;

    @InjectMocks
    private DailySummaryService dailySummaryService;

    private static final UUID ACCOUNT_ID = UUID.fromString("01a0726a-64e9-7712-9ecb-4996e2dcd75f");
    private static final UUID PET_A = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");
    private static final UUID STOP_A = UUID.fromString("cccccccc-0000-7000-8000-000000000003");
    private static final UUID STOP_B = UUID.fromString("cccccccc-0000-7000-8000-000000000004");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 9, 1, 11, 0);
    private static final LocalDateTime AFTERNOON = LocalDateTime.of(2026, 9, 1, 15, 0);

    // ── 제한 ─────────────────────────────────────────────────

    @Test
    @DisplayName("하루 한도를 다 쓰면 모델을 부르지 않는다")
    void 하루_한도_초과() {
        when(summaryRateLimitStore.tryConsumeDailyLimit(ACCOUNT_ID)).thenReturn(false);

        assertThatThrownBy(() -> dailySummaryService.generate(ACCOUNT_ID, DAY))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.SUMMARY_DAILY_LIMIT);

        verify(summaryRateLimitStore, never()).tryAcquireCooldown(any(), any());
        verify(llmProvider, never()).summarize(any());
    }

    @Test
    @DisplayName("쿨다운 중이면 모델을 부르지 않는다")
    void 쿨다운_중() {
        when(summaryRateLimitStore.tryConsumeDailyLimit(ACCOUNT_ID)).thenReturn(true);
        when(summaryRateLimitStore.tryAcquireCooldown(ACCOUNT_ID, DAY)).thenReturn(false);

        assertThatThrownBy(() -> dailySummaryService.generate(ACCOUNT_ID, DAY))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.SUMMARY_COOLDOWN);

        verify(llmProvider, never()).summarize(any());
        verify(visitLogRepository, never()).findAllByAccountIdAndDay(any(), any(), any());
    }

    @Test
    @DisplayName("한도를 먼저 보고 쿨다운을 나중에 본다")
    void 검사_순서() {
        when(summaryRateLimitStore.tryConsumeDailyLimit(ACCOUNT_ID)).thenReturn(false);

        assertThatThrownBy(() -> dailySummaryService.generate(ACCOUNT_ID, DAY))
                .isInstanceOf(CustomException.class);

        // 한도에 걸린 사람이 쿨다운 키를 새로 만들지 않아야 함
        // 순서가 뒤집히면 1분마다 의미 없는 키가 쌓임
        verify(summaryRateLimitStore, never()).tryAcquireCooldown(any(), any());
    }

    @Test
    @DisplayName("모델이 실패하면 쿨다운만 돌려주고 한도는 그대로 둔다")
    void 실패하면_쿨다운만_반환() {
        givenLimitPassed();
        givenMaterial();
        when(llmProvider.summarize(any())).thenReturn(null);

        assertThatThrownBy(() -> dailySummaryService.generate(ACCOUNT_ID, DAY))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.SUMMARY_GENERATION_FAILED);

        verify(summaryRateLimitStore).releaseCooldown(ACCOUNT_ID, DAY);
        verify(dailySummaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("장소를 못 불러와도 쿨다운을 돌려준다")
    void place_실패해도_쿨다운_반환() {
        givenLimitPassed();
        when(visitLogRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of(visitLog(PLACE_A, MORNING, null)));
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of());
        when(placeProvider.findByIds(anyCollection())).thenReturn(null);

        assertThatThrownBy(() -> dailySummaryService.generate(ACCOUNT_ID, DAY))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);

        verify(summaryRateLimitStore).releaseCooldown(ACCOUNT_ID, DAY);
    }

    @Test
    @DisplayName("그날 다녀온 곳도 담아 둔 곳도 없으면 만들지 않는다")
    void 재료_없음() {
        givenLimitPassed();
        when(visitLogRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of());
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of());
        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of());
        when(reviewProvider.findByAccountIdAndPeriod(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> dailySummaryService.generate(ACCOUNT_ID, DAY))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_FAILED);

        verify(llmProvider, never()).summarize(any());
        verify(summaryRateLimitStore).releaseCooldown(ACCOUNT_ID, DAY);
    }

    // ── 재료 ─────────────────────────────────────────────────

    @Test
    @DisplayName("다녀온 곳과 담아만 둔 곳이 다른 목록으로 넘어간다")
    void 재료_가르기() {
        givenLimitPassed();
        when(visitLogRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of(visitLog(PLACE_A, MORNING, null)));
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of(stop(PLACE_B, AFTERNOON, STOP_B)));
        when(placeProvider.findByIds(anyCollection())).thenReturn(Map.of(
                PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE"),
                PLACE_B, place(PLACE_B, "초록뜰 공원", "PARK")));
        when(reviewProvider.findByAccountIdAndPeriod(any(), any(), any())).thenReturn(List.of());
        when(llmProvider.summarize(any())).thenReturn("요약 문장입니다.");
        when(dailySummaryRepository.findById(any())).thenReturn(Optional.empty());
        when(dailySummaryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        dailySummaryService.generate(ACCOUNT_ID, DAY);

        SummaryData material = capturedMaterial();
        assertThat(material.visited()).extracting(SummaryData.PlaceMaterial::name)
                .containsExactly("멍멍 카페");
        assertThat(material.planned()).extracting(SummaryData.PlaceMaterial::name)
                .containsExactly("초록뜰 공원");
    }

    @Test
    @DisplayName("이미 다녀온 일정은 담아 둔 곳에서 빠진다")
    void 다녀온_일정은_제외() {
        givenLimitPassed();
        when(visitLogRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of(visitLog(PLACE_A, MORNING, STOP_A)));
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of(stop(PLACE_A, MORNING, STOP_A)));
        when(placeProvider.findByIds(anyCollection()))
                .thenReturn(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(reviewProvider.findByAccountIdAndPeriod(any(), any(), any())).thenReturn(List.of());
        when(llmProvider.summarize(any())).thenReturn("요약 문장입니다.");
        when(dailySummaryRepository.findById(any())).thenReturn(Optional.empty());
        when(dailySummaryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        dailySummaryService.generate(ACCOUNT_ID, DAY);

        SummaryData material = capturedMaterial();
        assertThat(material.visited()).hasSize(1);
        assertThat(material.planned()).isEmpty();
    }

    @Test
    @DisplayName("후기가 하나도 없어도 요약을 만든다")
    void 후기_없이도_만듦() {
        givenLimitPassed();
        givenMaterial();
        when(llmProvider.summarize(any())).thenReturn("멍멍 카페를 다녀온 하루였어요.");
        when(dailySummaryRepository.findById(any())).thenReturn(Optional.empty());
        when(dailySummaryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        DailySummaryOutput output = dailySummaryService.generate(ACCOUNT_ID, DAY);

        assertThat(capturedMaterial().reviews()).isEmpty();
        assertThat(output.summary()).isEqualTo("멍멍 카페를 다녀온 하루였어요.");
    }

    @Test
    @DisplayName("후기를 그날 하루로 범위를 좁혀 물어본다")
    void 후기_조회_범위() {
        givenLimitPassed();
        givenMaterial();
        when(llmProvider.summarize(any())).thenReturn("요약 문장입니다.");
        when(dailySummaryRepository.findById(any())).thenReturn(Optional.empty());
        when(dailySummaryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        dailySummaryService.generate(ACCOUNT_ID, DAY);

        verify(reviewProvider).findByAccountIdAndPeriod(ACCOUNT_ID, DAY, DAY);
    }

    @Test
    @DisplayName("그날 하루만 조회하며 다음 날 00:00 을 빼고 묻는다")
    void 날짜_경계() {
        givenLimitPassed();
        givenMaterial();
        when(llmProvider.summarize(any())).thenReturn("요약 문장입니다.");
        when(dailySummaryRepository.findById(any())).thenReturn(Optional.empty());
        when(dailySummaryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        dailySummaryService.generate(ACCOUNT_ID, DAY);

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(visitLogRepository)
                .findAllByAccountIdAndDay(eq(ACCOUNT_ID), start.capture(), end.capture());

        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 2, 0, 0));
    }

    // ── 저장 ─────────────────────────────────────────────────

    @Test
    @DisplayName("처음 만들면 새 요약이 저장된다")
    void 첫_요약() {
        givenLimitPassed();
        givenMaterial();
        when(llmProvider.summarize(any())).thenReturn("첫 문장입니다.");
        when(dailySummaryRepository.findById(any())).thenReturn(Optional.empty());
        when(dailySummaryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        DailySummaryOutput output = dailySummaryService.generate(ACCOUNT_ID, DAY);

        assertThat(output.visitDate()).isEqualTo(DAY);
        assertThat(output.summary()).isEqualTo("첫 문장입니다.");
        assertThat(output.generatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 있으면 그 행을 고치고 새 객체로 덮어쓰지 않는다")
    void 갱신() {
        DailySummary existing = DailySummary.create(
                ACCOUNT_ID, DAY, "옛 문장입니다.", LocalDateTime.of(2026, 9, 1, 20, 0));

        givenLimitPassed();
        givenMaterial();
        when(llmProvider.summarize(any())).thenReturn("새 문장입니다.");
        when(dailySummaryRepository.findById(any())).thenReturn(Optional.of(existing));
        when(dailySummaryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        DailySummaryOutput output = dailySummaryService.generate(ACCOUNT_ID, DAY);

        // 찾아온 그 객체가 고쳐져 그대로 넘어가야 함
        // 새로 만든 객체를 넘기면 생성 시각이 비어 있어 갱신이 실패함
        ArgumentCaptor<DailySummary> captor = ArgumentCaptor.forClass(DailySummary.class);
        verify(dailySummaryRepository).save(captor.capture());

        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getSummary()).isEqualTo("새 문장입니다.");
        assertThat(output.summary()).isEqualTo("새 문장입니다.");
    }

    // ── 아래는 준비를 돕는 것들임 ─────────────────────────────

    private void givenLimitPassed() {
        when(summaryRateLimitStore.tryConsumeDailyLimit(ACCOUNT_ID)).thenReturn(true);
        when(summaryRateLimitStore.tryAcquireCooldown(ACCOUNT_ID, DAY)).thenReturn(true);
    }

    /**
     * 다녀온 곳 하나만 있는 하루를 만듭니다.
     */
    private void givenMaterial() {
        when(visitLogRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of(visitLog(PLACE_A, MORNING, null)));
        when(itineraryStopRepository.findAllByAccountIdAndDay(any(), any(), any()))
                .thenReturn(List.of());
        when(placeProvider.findByIds(anyCollection()))
                .thenReturn(Map.of(PLACE_A, place(PLACE_A, "멍멍 카페", "CAFE")));
        when(reviewProvider.findByAccountIdAndPeriod(any(), any(), any()))
                .thenReturn(List.<ReviewData>of());
    }

    /**
     * 모델에게 넘긴 재료를 꺼냅니다.
     *
     * 응답에 안 실리는 값이라 호출 인자를 붙잡아 확인합니다.
     */
    private SummaryData capturedMaterial() {
        ArgumentCaptor<SummaryData> captor = ArgumentCaptor.forClass(SummaryData.class);
        verify(llmProvider).summarize(captor.capture());
        return captor.getValue();
    }

    private PlaceData place(UUID placeId, String name, String placeType) {
        return new PlaceData(placeId, name, placeType, null, null, null, false);
    }

    private VisitLog visitLog(UUID placeId, LocalDateTime visitedAt, UUID stopId) {
        return VisitLog.create(ACCOUNT_ID, placeId, PET_A, visitedAt,
                Verdict.ALLOWED, stopId, null);
    }

    private ItineraryStop stop(UUID placeId, LocalDateTime visitAt, UUID id) {
        ItineraryStop stop = ItineraryStop.create(ACCOUNT_ID, placeId, visitAt, PET_A, 1, null);
        setField(stop, "id", id);
        return stop;
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
