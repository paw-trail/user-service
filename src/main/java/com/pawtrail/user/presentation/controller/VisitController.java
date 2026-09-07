package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.user.application.dto.output.VisitCardOutput;
import com.pawtrail.user.application.dto.output.VisitCreateOutput;
import com.pawtrail.user.application.service.VisitService;
import com.pawtrail.user.presentation.request.VisitCreateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방문 기록 API 입니다.
 *
 * 누구의 기록인지는 요청에서 받지 않습니다.
 * 게이트웨이가 토큰을 검증해 X-User-Id 헤더로 넣어 주고,
 * 공통 모듈의 필터가 그것을 CustomUserPrincipal 로 만들어 둡니다.
 *
 * 경로에 accountId 를 두면 남의 것을 부를 수 있게 되므로 그렇게 하지 않습니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    /**
     * 내 방문 기록 목록을 봅니다.
     *
     * 마이페이지 동반 기록과 방문한 장소가 같은 API 를 씁니다.
     *
     * 순서는 날짜가 최신순이고 하루 안은 시간순입니다.
     * 목록을 열면 최근 다녀온 날이 위에 있어야 하고,
     * 하루 안에서는 동선이 순서대로 보여야 합니다.
     *
     * summary 는 그날 첫 행에만 실립니다.
     * 하루 요약이 하루 한 줄인데 응답은 방문 단위 배열이라
     * 그대로 두면 같은 문장이 그날 방문 수만큼 나갑니다.
     * 프론트는 summary 가 있는 행에서만 요약을 그리면 됩니다.
     *
     * 판정 배지는 저장된 값이라 verdict 를 부르지 않습니다.
     * 다만 준비물은 저장하는 자리가 없어 볼 때마다 불러 채웁니다.
     * 한 카드 안에서 배지는 그때의 사실이고 준비물은 지금의 안내입니다.
     *
     * 셋의 실패를 다르게 다룹니다.
     *   place    목록 전체가 502 로 실패합니다. 장소 이름이 없으면 카드가 성립하지 않습니다.
     *   verdict  준비물만 비고 목록은 그대로 내려갑니다.
     *   review   별점만 비고 목록은 그대로 내려갑니다.
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<VisitCardOutput>>> getMyVisits(
            @CurrentUser CustomUserPrincipal principal) {

        List<VisitCardOutput> response = visitService.getMyVisits(principal.accountId());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 방문을 기록합니다.
     *
     * 지난 날짜 일정 카드의 다녀왔어요 가 부릅니다.
     * 누른 뒤에도 일정은 그대로 남고 카드에 방문함 표시만 붙습니다.
     * 일정을 지우는 것이 아니라 방문 기록을 하나 더 만드는 것입니다.
     *
     * 같은 일정을 두 번 눌러도 성공하며 기존 식별자가 나갑니다.
     * 정상 흐름에서는 이미 눌렀으면 버튼 대신 방문함 표시가 떠 중복 요청이 안 나가고,
     * 더블클릭이나 두 탭에서만 나옵니다.
     *
     * 판정을 못 받아오면 요청 자체가 502 로 실패합니다.
     * 판정 스냅샷은 나중에 고칠 방법이 없어 틀린 값을 남기면 안 되기 때문입니다.
     * 며칠 전 일정을 지금 기록하는 동작이라 몇 분 뒤에 다시 눌러도 손해가 없습니다.
     *
     * 201 이 아니라 200 인 이유는 멱등이기 때문입니다.
     * 이미 기록된 경우에는 새로 만들어진 것이 없어 201 이 사실과 다릅니다.
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<VisitCreateOutput>> record(
            @CurrentUser CustomUserPrincipal principal,
            @Valid @RequestBody VisitCreateRequest request) {

        VisitCreateOutput response =
                visitService.record(principal.accountId(), request.toInput());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 방문 기록을 지웁니다.
     *
     * 마이페이지 동반 기록의 기록 삭제가 부릅니다.
     * 목록이 이 API 라 응답 식별자가 visitId 이고 프론트가 아는 값도 그것입니다.
     *
     * 여정 화면의 일정 삭제와는 지우는 대상이 다릅니다.
     *   여정 화면   DELETE /api/v1/itineraries/{stopId}   담아 둔 일정을 뺌
     *   동반 기록   DELETE /api/v1/visits/{visitId}       다녀온 기록을 지움
     *
     * 남의 것을 지우려 하거나 없는 것을 지우려 하면 둘 다 404 입니다.
     * 403 을 내면 그 visitId 가 존재한다는 것을 알려주는 셈이 됩니다.
     */
    @DeleteMapping("/{visitId}")
    public ResponseEntity<CommonApiResponse<Void>> remove(
            @CurrentUser CustomUserPrincipal principal,
            @PathVariable UUID visitId) {

        visitService.remove(principal.accountId(), visitId);
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }
}
