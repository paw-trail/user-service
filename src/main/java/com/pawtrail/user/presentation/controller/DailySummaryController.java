package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.user.application.dto.output.DailySummaryOutput;
import com.pawtrail.user.application.service.DailySummaryService;
import com.pawtrail.user.presentation.request.DailySummaryCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하루 요약 API 입니다.
 *
 * 경로가 프로필 API 와 같은 앞부분을 씁니다.
 * 여러 컨트롤러가 같은 앞부분을 나눠 가져도 되며,
 * 이 저장소는 즐겨찾기와 방문 기록과 일정을 그렇게 도메인별로 나눠 왔습니다.
 *
 * 누구의 요약인지는 요청에서 받지 않습니다.
 * 게이트웨이가 토큰을 검증해 X-User-Id 헤더로 넣어 주고,
 * 공통 모듈의 필터가 그것을 사용자 정보로 만들어 둡니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class DailySummaryController {

    private final DailySummaryService dailySummaryService;

    /**
     * 그날의 요약을 만들거나 다시 만듭니다.
     *
     * 마이페이지 동반 기록의 날짜 카드에서 부릅니다.
     * 요약이 없으면 만들기 버튼이, 있으면 문장과 갱신하기 버튼이 뜨는데 둘이 같은 요청입니다.
     *
     * 사용자가 눌러야 돕니다.
     * 자동 생성이나 배치를 두지 않는 이유는 일정을 여러 번 나눠 담기 때문입니다.
     * 변경마다 만들면 하루치를 대여섯 번 다시 만들게 되는데 중간 상태의 요약은 아무도 보지 않습니다.
     *
     * 응답이 몇 초 걸립니다.
     * 언어 모델을 부르는 자리라 화면이 그동안 로딩을 띄워야 합니다.
     *
     * 실패가 셋으로 갈립니다.
     *   같은 날짜를 너무 빨리 다시 요청  429
     *   하루에 만들 수 있는 횟수를 다 씀  429
     *   문장을 만들지 못함              502
     * 앞의 둘은 코드가 달라 화면이 안내를 가려 쓸 수 있습니다.
     *
     * 201 이 아니라 200 입니다.
     * 이미 있는 요약을 덮어쓰는 경우가 있어 새로 만들어졌다고 단정할 수 없습니다.
     */
    @PostMapping("/daily-summary")
    public ResponseEntity<CommonApiResponse<DailySummaryOutput>> generate(
            @CurrentUser CustomUserPrincipal principal,
            @Valid @RequestBody DailySummaryCreateRequest request) {

        DailySummaryOutput response =
                dailySummaryService.generate(principal.accountId(), request.visitDate());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }
}
