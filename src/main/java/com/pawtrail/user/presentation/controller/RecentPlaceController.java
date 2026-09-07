package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.user.application.dto.output.RecentPlaceCardOutput;
import com.pawtrail.user.application.service.RecentPlaceService;
import com.pawtrail.user.presentation.request.RecentPlaceCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 최근에 본 장소 API 입니다.
 *
 * 메인 화면 아래쪽의 최근 확인해본 동반 장소가 이 값을 씁니다.
 * 인기 급상승과 짝을 이루지만 소유한 서비스가 다릅니다.
 * 그쪽은 전체 집계라 검색이 갖고 이쪽은 개인 이력이라 여기가 갖습니다.
 *
 * 표를 만들지 않고 Redis 목록에 둡니다.
 * 최근 스물 곳만 남기면 되는 값이라 오래된 것이 밀려나야 하는데,
 * 데이터베이스에 두면 그 정리를 우리가 짜야 하고 남길 가치도 없는 이력이 쌓입니다.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/users/me/recent-places")
@RequiredArgsConstructor
public class RecentPlaceController {

    private final RecentPlaceService recentPlaceService;

    /**
     * 방금 본 장소를 기록합니다.
     *
     * 프론트가 장소 상세를 열거나 검색 결과에서 장소를 누를 때 자동으로 보냅니다.
     * 사용자가 누르는 버튼이 따로 있는 것이 아닙니다.
     *
     * 이 API 는 명세에 없어 우리가 신설했습니다.
     * 읽는 자리만 있고 쓰는 자리가 없어 목록이 영원히 비는 상태였습니다.
     *
     * 장소 서비스가 조회 중에 우리를 부르는 방법도 있었으나 그렇게 하지 않았습니다.
     * 조회는 자주 불리는 길인데 거기에 쓰기를 끼워 넣게 되기 때문입니다.
     *
     * 응답에 담을 것이 없습니다.
     * 프론트가 화면을 여는 김에 보내는 것이라 결과를 기다리지도 않습니다.
     *
     * 같은 장소를 다시 봐도 됩니다.
     * 목록에 이미 있으면 그것을 빼고 다시 맨 앞에 놓으므로 중복이 쌓이지 않습니다.
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<Void>> record(
            @CurrentUser CustomUserPrincipal principal,
            @Valid @RequestBody RecentPlaceCreateRequest request) {

        recentPlaceService.record(principal.accountId(), request.placeId());
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    /**
     * 최근에 본 장소를 봅니다.
     *
     * 가장 최근에 본 것이 맨 앞입니다.
     *
     * 카드 모양은 즐겨찾기와 같습니다.
     * 다만 하트 상태를 함께 싣습니다.
     * 즐겨찾기 목록은 그 표에서 나와 거기 있는 것이 곧 담긴 것이지만,
     * 이 목록은 다른 곳에서 나오므로 어느 것을 담아뒀는지 따로 확인해야 합니다.
     *
     * 몇 장을 받을지는 화면이 정합니다.
     * 서버는 스무 곳까지만 담아 두므로 그보다 큰 값을 보내도 있는 만큼만 옵니다.
     * 상한을 둔 것은 실수로 큰 값을 보냈을 때 뜻 없는 요청이 되지 않게 하기 위함입니다.
     *
     * 셋의 실패를 다르게 다룹니다.
     *   장소   목록 전체가 502 로 실패합니다. 이름이 없으면 카드가 성립하지 않습니다.
     *   판정   배지와 준비물만 비고 목록은 그대로 내려갑니다.
     *   후기   별점만 비고 목록은 그대로 내려갑니다.
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<RecentPlaceCardOutput>>> getRecent(
            @CurrentUser CustomUserPrincipal principal,
            @RequestParam(name = "size", defaultValue = "10")
            @Positive(message = "size 는 1 이상이어야 합니다")
            @Max(value = 20, message = "size 는 20까지 쓸 수 있습니다") int size) {

        List<RecentPlaceCardOutput> response =
                recentPlaceService.getRecent(principal.accountId(), size);
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }
}
