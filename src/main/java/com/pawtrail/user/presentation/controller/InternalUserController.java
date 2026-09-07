package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.user.application.dto.output.UserSummaryOutput;
import com.pawtrail.user.application.service.UserProfileService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스가 부르는 조회입니다.
 *
 * 게이트웨이는 /internal 을 라우팅하지 않습니다.
 * 브라우저에서 localhost:8080/internal/... 로 부를 수 없고 같은 VPC 안에서만 닿습니다.
 * 그래서 이 경로에는 인증 토큰이 실려 오지 않고, 공통 보안 체인도 열어 두었습니다.
 *
 * 다만 인증이 없다는 것이 소유권 검증 면제는 아닙니다.
 * accountId 를 받아 그 사람의 것을 돌려주는 API 라면
 * 게이트웨이가 넣은 X-User-Id 와 대조해야 합니다.
 *
 * 이 API 는 그 경우가 아닙니다.
 * 여러 사람의 이름을 한 번에 채우는 배치 조회라 "내 것" 이라는 개념이 없고,
 * 돌려주는 닉네임과 사진은 후기 목록에 그대로 나오는 값입니다.
 * accountId 도 그 목록에 이미 있어 숨길 것이 없습니다.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserProfileService userProfileService;

    /**
     * 여러 사람의 닉네임과 프로필 사진을 한 번에 돌려줍니다.
     *
     * review 가 후기 목록의 작성자를, report 가 관리자 화면의 제보자를 채울 때 부릅니다.
     * 목록 한 쪽에 사람이 여럿이라 한 명씩 물어보면 요청이 그 수만큼 늘어납니다.
     *
     * 없는 식별자는 결과에서 그냥 빠집니다. 오류로 보지 않습니다.
     * 부르는 쪽이 자기 목록과 맞춰 쓰므로 빠진 것은 이름 없이 그리면 됩니다.
     * 탈퇴한 사람도 빠지는데, 그 경우 부르는 쪽이 "탈퇴한 사용자" 로 표시합니다.
     */
    @GetMapping("/users")
    public ResponseEntity<CommonApiResponse<List<UserSummaryOutput>>> getUsers(
            @RequestParam("ids") List<UUID> ids) {

        List<UserSummaryOutput> response = userProfileService.getSummaries(ids);
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }
}
