package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.user.application.dto.output.ItineraryCardOutput;
import com.pawtrail.user.application.dto.output.ItineraryCreateOutput;
import com.pawtrail.user.application.service.ItineraryService;
import com.pawtrail.user.presentation.request.ItineraryCreateRequest;
import com.pawtrail.user.presentation.request.ItineraryUpdateRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 날짜 단위 일정 API 입니다.
 *
 * 누구의 일정인지는 요청에서 받지 않습니다.
 * 게이트웨이가 토큰을 검증해 X-User-Id 헤더로 넣어 주고,
 * 공통 모듈의 필터가 그것을 CustomUserPrincipal 로 만들어 둡니다.
 *
 * 경로에 accountId 를 두면 남의 것을 부를 수 있게 되므로 그렇게 하지 않습니다.
 *
 * 순서를 통째로 다시 매기는 API 는 두지 않았습니다.
 * 목록 정렬이 방문 예정 시각을 먼저 보므로 시각을 고치는 것이 곧 순서 변경이고,
 * 화면에도 드래그로 재배치하는 자리가 없습니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    /**
     * 그날 일정 목록을 봅니다.
     *
     * 날짜 하나를 골라 그날 것만 봅니다.
     * 제목이 있는 여행을 묶는 단위가 없어 하루가 곧 일정입니다.
     *
     * 순서는 방문 예정 시각 순이고 시각이 같으면 담은 순서입니다.
     * 화면이 곧 그날의 동선이라 먼저 갈 곳이 위에 옵니다.
     *
     * 판정은 지금 조건으로 계산한 값입니다.
     * 아직 안 간 곳이라 즐겨찾기와 같은 성격이며,
     * 다녀온 시점을 박아 두는 방문 기록의 배지와는 다릅니다.
     *
     * 좌표와 카테고리가 함께 실립니다.
     * 프론트가 그 값으로 지도 마커를 찍고 색을 가르며 경로선을 그립니다.
     *
     * 셋의 실패를 다르게 다룹니다.
     *   place    목록 전체가 502 로 실패합니다. 장소 이름이 없으면 카드가 성립하지 않습니다.
     *   verdict  배지와 준비물만 비고 목록은 그대로 내려갑니다.
     *   review   별점만 비고 목록은 그대로 내려갑니다.
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<ItineraryCardOutput>>> getMyItinerary(
            @CurrentUser CustomUserPrincipal principal,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<ItineraryCardOutput> response =
                itineraryService.getMyItinerary(principal.accountId(), date);
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 장소를 일정에 담습니다.
     *
     * 장소 상세의 일정 추가 폼이 부릅니다.
     * 일정 화면에서 장소를 담는 자리는 없습니다.
     *
     * 같은 시각에 같은 장소를 이미 담아 두었으면 새로 만들지 않고
     * 그 식별자를 돌려줍니다.
     * 같은 장소를 하루에 여러 번 담는 것 자체는 정상이라 시각이 다르면 다른 항목입니다.
     *
     * 201 이 아니라 200 인 이유는 그 멱등 때문입니다.
     * 이미 담긴 경우에는 새로 만들어진 것이 없어 201 이 사실과 다릅니다.
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<ItineraryCreateOutput>> add(
            @CurrentUser CustomUserPrincipal principal,
            @Valid @RequestBody ItineraryCreateRequest request) {

        ItineraryCreateOutput response =
                itineraryService.add(principal.accountId(), request.toInput());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 담아 둔 일정을 고칩니다.
     *
     * 보낸 항목만 바꿉니다.
     * 동반 동물과 메모는 null 을 보내면 비웁니다.
     * 방문 예정 일시는 지울 수 없어 null 을 보내면 400 입니다.
     *
     * 날짜는 바꿀 수 없습니다.
     * 일정을 묶는 표가 없어 날짜가 곧 그 일정의 정체성이고,
     * 다른 날로 옮기면 보고 있던 목록에서 카드가 사라집니다.
     * 화면 수정 폼에 날짜 칸이 없어도 요청은 일시를 통째로 보내므로 서버가 막습니다.
     *
     * 고친 시각에 같은 장소가 이미 담겨 있으면 400 입니다.
     * 담기는 그 상태를 멱등으로 접지만 수정은 두 행을 하나로 합칠 수 없습니다.
     *
     * 응답에 담을 것이 없습니다.
     * 바뀐 값은 프론트가 이미 알고 있고, 목록을 다시 부르면 조립된 카드가 옵니다.
     */
    @PatchMapping("/{stopId}")
    public ResponseEntity<CommonApiResponse<Void>> update(
            @CurrentUser CustomUserPrincipal principal,
            @PathVariable UUID stopId,
            @Valid @RequestBody ItineraryUpdateRequest request) {

        itineraryService.update(principal.accountId(), stopId, request.toInput());
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    /**
     * 담아 둔 일정을 뺍니다.
     *
     * 연결된 방문 기록도 함께 지웁니다.
     * 방문 기록이 어느 일정에서 온 것인지를 가리키고 있어
     * 일정만 지우면 없는 식별자를 가리키는 행이 남습니다.
     *
     * 방문 기록만 지우는 것은 다른 API 입니다.
     *   여정 화면      DELETE /api/v1/itineraries/{stopId}   일정과 그 방문 기록
     *   동반 기록      DELETE /api/v1/visits/{visitId}       방문 기록만
     *
     * 남의 것을 지우려 하거나 없는 것을 지우려 하면 둘 다 404 입니다.
     * 403 을 내면 그 stopId 가 존재한다는 것을 알려주는 셈이 됩니다.
     */
    @DeleteMapping("/{stopId}")
    public ResponseEntity<CommonApiResponse<Void>> remove(
            @CurrentUser CustomUserPrincipal principal,
            @PathVariable UUID stopId) {

        itineraryService.remove(principal.accountId(), stopId);
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    /**
     * 그 범위에서 일정이 있는 날짜만 봅니다.
     *
     * 달력이 씁니다.
     * 아무 날짜나 고를 수 있으면 빈 날이 대부분이라 어느 날에 일정이 있는지를 미리 표시합니다.
     * 한 번에 한 달을 그리므로 범위가 곧 그 달의 첫날과 마지막 날입니다.
     *
     * 범위에 상한을 두지 않습니다.
     * 호출 형태가 이미 한 달로 좁혀져 있고, 한 사람의 일정이라 결과가 작습니다.
     *
     * 시작이 끝보다 늦으면 400 입니다.
     * 정상 흐름에서는 나오지 않으며 조용히 빈 배열을 주면 원인이 드러나지 않습니다.
     *
     * 응답은 날짜 문자열 배열입니다.
     * 카드도 장소도 실리지 않아 달을 넘길 때마다 불러도 가볍습니다.
     */
    @GetMapping("/dates")
    public ResponseEntity<CommonApiResponse<List<LocalDate>>> getDates(
            @CurrentUser CustomUserPrincipal principal,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<LocalDate> response = itineraryService.getDates(principal.accountId(), from, to);
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }
}
