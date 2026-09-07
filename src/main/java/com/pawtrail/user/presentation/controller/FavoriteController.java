package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.user.application.dto.output.FavoriteCardOutput;
import com.pawtrail.user.application.service.FavoriteService;
import com.pawtrail.user.presentation.request.FavoriteCreateRequest;
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
 * 즐겨찾기 API 입니다.
 *
 * 누구의 즐겨찾기인지는 요청에서 받지 않습니다.
 * 게이트웨이가 토큰을 검증해 X-User-Id 헤더로 넣어 주고,
 * 공통 모듈의 필터가 그것을 CustomUserPrincipal 로 만들어 둡니다.
 *
 * 경로에 accountId 를 두면 남의 것을 부를 수 있게 되므로 그렇게 하지 않습니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 내 즐겨찾기 목록을 봅니다.
     *
     * place 와 verdict 와 review 를 불러 카드를 조립합니다.
     * 그 서비스들이 아직 없어 지금은 스텁을 띄워야 목록이 보입니다.
     *
     * 셋의 실패를 다르게 다룹니다.
     *   place    목록 전체가 502 로 실패합니다. 장소 이름이 없으면 카드가 성립하지 않습니다.
     *   verdict  배지만 비고 목록은 그대로 내려갑니다.
     *   review   별점만 비고 목록은 그대로 내려갑니다.
     *
     * 페이징하지 않습니다.
     * 프론트가 응답 전체를 placeType 으로 세어 카테고리 칩을 만들고
     * 0건인 칩은 그리지 않기로 되어 있어, 페이지로 자르면 그 규칙이 깨집니다.
     *
     * 순서는 최근에 담은 것부터입니다.
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<FavoriteCardOutput>>> getMyFavorites(
            @CurrentUser CustomUserPrincipal principal) {

        List<FavoriteCardOutput> response =
                favoriteService.getMyFavorites(principal.accountId());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 즐겨찾기에 담습니다.
     *
     * 이미 담아 둔 장소를 다시 담아도 성공입니다.
     * 하트는 결과 상태를 만드는 동작이라 두 번 눌러도 "담긴 상태" 가 되면 됩니다.
     * 정상 흐름에서는 이미 담긴 것이 꽉 찬 하트로 보여 중복 요청이 나가지 않고,
     * 더블클릭이나 두 탭에서만 나오는데 거기에 오류를 띄우면 오히려 이상합니다.
     *
     * 응답에 값을 담지 않습니다.
     * 프론트가 어떤 장소를 담았는지 이미 알고 있고 화면에 반영할 다른 값이 없습니다.
     *
     * 201 이 아니라 200 인 이유는 멱등이기 때문입니다.
     * 이미 담긴 경우에는 새로 만들어진 것이 없어 201 이 사실과 다릅니다.
     * 두 경우의 상태 코드를 가르려면 조회를 한 번 더 해야 하는데,
     * 그렇게 얻는 것이 프론트가 쓰지 않는 구분 하나뿐입니다.
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<Void>> add(
            @CurrentUser CustomUserPrincipal principal,
            @Valid @RequestBody FavoriteCreateRequest request) {

        favoriteService.add(principal.accountId(), request.toInput());
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }

    /**
     * 즐겨찾기를 해제합니다.
     *
     * favoriteId 가 아니라 placeId 를 받습니다.
     * 하트를 누르는 자리가 장소 카드라 프론트가 아는 값이 placeId 뿐입니다.
     *
     * 담아 두지 않은 장소를 해제해도 성공입니다.
     * 담기가 멱등인데 해제만 404 를 내면 같은 하트 버튼이 방향에 따라 다르게 동작합니다.
     */
    @DeleteMapping("/{placeId}")
    public ResponseEntity<CommonApiResponse<Void>> remove(
            @CurrentUser CustomUserPrincipal principal,
            @PathVariable UUID placeId) {

        favoriteService.remove(principal.accountId(), placeId);
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }
}
