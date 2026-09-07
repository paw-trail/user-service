package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.user.application.service.FavoriteService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스가 부르는 즐겨찾기 조회입니다.
 *
 * 게이트웨이는 /internal 을 라우팅하지 않습니다.
 * 브라우저에서 localhost:8080/internal/... 로 부를 수 없고 같은 VPC 안에서만 닿습니다.
 * 그래서 이 경로에는 인증 토큰이 실려 오지 않고, 공통 보안 체인도 열어 두었습니다.
 *
 * InternalUserController 에 얹지 않고 클래스를 따로 둡니다.
 * 이 레포는 자원별로 컨트롤러가 갈려 있고,
 * /internal 만 한 클래스에 모으면 방문 기록과 하루 요약이 붙을 때 그 클래스만 계속 커집니다.
 * InternalUserController 라는 이름이 즐겨찾기까지 가지면 이름과 내용도 어긋납니다.
 *
 * 밖에서 보이는 것은 한 클래스에 두었을 때와 완전히 같습니다.
 * 스프링이 경로를 클래스 단위가 아니라 매핑 테이블 하나로 관리하기 때문입니다.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalFavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 그 장소를 담아 둔 사람들의 계정 식별자를 돌려줍니다.
     *
     * notification 이 부릅니다.
     * 장소의 동반 조건이 바뀌면 policy.changed 가 발행되는데,
     * 그때 누구에게 알릴지를 이 조회로 정합니다.
     * 즐겨찾기 목록에 판정 배지를 넣기로 한 것도 그 알림과 짝입니다.
     *
     * 소유권을 검증하지 않습니다.
     * accountId 를 받아 그 사람의 것을 돌려주는 API 라면 X-User-Id 와 대조해야 하지만,
     * 이 API 는 장소를 받아 여러 사람을 돌려주므로 "내 것" 이라는 개념이 없습니다.
     * GET /internal/users?ids= 와 같은 성격입니다.
     *
     * 페이징합니다.
     * 사용자가 보는 GET /api/v1/favorites 는 한 사람이 모은 것이라 건수에 상한이 있지만,
     * 이쪽은 한 장소에 몰린 사람 수라 인기 장소면 수천 명일 수 있습니다.
     * 브라우저가 보는 화면이 아니라 카테고리 칩 같은 규칙도 걸리지 않고,
     * 부르는 쪽도 한 사람씩 알림 행을 만드는 배치라 페이지 단위 처리가 자연스럽습니다.
     *
     * 계정 식별자만 담습니다.
     * 부르는 쪽이 하는 일이 "이 사람들에게 알림을 만든다" 뿐이고
     * 담은 시각이나 메모는 알림 문구에도 수신 설정 판단에도 쓰이지 않습니다.
     */
    @GetMapping("/favorites")
    public ResponseEntity<CommonApiResponse<PageResponse<UUID>>> getAccountIds(
            @RequestParam("placeId") UUID placeId,
            @PageableDefault(size = 100) Pageable pageable) {

        Page<UUID> accountIds = favoriteService.getAccountIdsByPlaceId(placeId, pageable);
        return ResponseEntity.ok(CommonApiResponse.success(PageResponse.from(accountIds)));
    }
}
