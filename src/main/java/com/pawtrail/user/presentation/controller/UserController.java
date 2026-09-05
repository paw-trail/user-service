package com.pawtrail.user.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.security.annotation.CurrentUser;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.user.application.dto.output.ProfileOutput;
import com.pawtrail.user.application.dto.output.UploadUrlOutput;
import com.pawtrail.user.application.service.UserProfileService;
import com.pawtrail.user.presentation.request.DefaultPetUpdateRequest;
import com.pawtrail.user.presentation.request.ProfileUpdateRequest;
import com.pawtrail.user.presentation.request.UploadUrlRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지가 쓰는 프로필 API 입니다.
 *
 * 누구의 프로필인지는 요청 본문이나 경로에서 받지 않습니다.
 * 게이트웨이가 토큰을 검증해 X-User-Id 헤더로 넣어 주고,
 * 공통 모듈의 필터가 그것을 CustomUserPrincipal 로 만들어 둡니다.
 * 그래서 @CurrentUser 로 꺼내 쓰기만 하면 됩니다.
 *
 * 경로에 accountId 를 두면 남의 것을 부를 수 있게 되므로 그렇게 하지 않습니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    /**
     * 내 프로필을 봅니다.
     *
     * stats 의 reviewCount 는 지금 항상 null 입니다.
     * review 서비스가 아직 없기 때문이며 명세도 그 상태를 허용합니다.
     *
     * 가입 직후에 부르면 404 가 날 수 있습니다.
     * 회원가입이 자동 로그인이라 account.created 를 처리하기 전에 요청이 닿을 수 있습니다.
     * 실제 창은 밀리초이고 프론트가 짧게 재시도합니다.
     */
    @GetMapping("/me")
    public ResponseEntity<CommonApiResponse<ProfileOutput>> getMyProfile(
            @CurrentUser CustomUserPrincipal principal) {

        ProfileOutput response = userProfileService.getMyProfile(principal.accountId());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 닉네임과 프로필 사진을 바꿉니다.
     *
     * 사용자가 고칠 수 있는 것은 이 둘뿐입니다.
     * 대표 반려동물은 아래 API 가 따로 있고 stats 는 조회할 때 계산하는 값입니다.
     *
     * 두 값 모두 null 을 보내면 지웁니다.
     *
     * 수정한 프로필을 그대로 돌려줍니다.
     * 프론트가 화면을 다시 그리려고 GET 을 또 부르지 않아도 됩니다.
     */
    @PatchMapping("/me")
    public ResponseEntity<CommonApiResponse<ProfileOutput>> updateMyProfile(
            @CurrentUser CustomUserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {

        ProfileOutput response =
                userProfileService.updateProfile(principal.accountId(), request.toInput());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 프로필 사진을 올릴 주소를 발급합니다.
     *
     * 이 API 는 파일을 받지 않습니다. 주소만 돌려줍니다.
     * 이름이 uploads 가 아니라 upload-url 인 이유가 그것입니다.
     *
     * 프론트가 할 일이 셋입니다.
     *   ① 여기서 uploadUrl 과 fileUrl 을 받음
     *      *요청에 contentType 과 contentLength 를 함께 보낼 것
     *        contentLength 는 올릴 파일의 정확한 바이트 수임(file.size)
     *   ② uploadUrl 로 파일을 PUT
     *      *Content-Type 헤더를 요청한 값과 똑같이 보낼 것
     *      *파일 크기도 요청한 값과 똑같아야 함
     *        둘 다 서명에 들어 있어 하나라도 다르면 S3 가 403 을 냄
     *   ③ fileUrl 을 PATCH /users/me 의 profileImageUrl 에 담아 보냄
     *
     * ②까지만 하고 ③을 안 하면 파일은 올라갔는데 프로필에 안 붙습니다.
     * 키가 계정당 하나로 고정이라 다음에 올리면 그 자리에 덮어써집니다.
     */
    @PostMapping("/me/upload-url")
    public ResponseEntity<CommonApiResponse<UploadUrlOutput>> issueUploadUrl(
            @CurrentUser CustomUserPrincipal principal,
            @Valid @RequestBody UploadUrlRequest request) {

        UploadUrlOutput response = userProfileService.issueUploadUrl(
                principal.accountId(), request.contentType(), request.contentLength());
        return ResponseEntity.ok(CommonApiResponse.success(response));
    }

    /**
     * 대표 반려동물을 바꿉니다.
     *
     * petId 가 null 이면 해제입니다. 잘못된 요청이 아닙니다.
     * 반려동물이 0마리인 상태를 정식으로 지원합니다.
     *
     * 응답에 값을 담지 않습니다.
     * 프론트가 이 요청을 보낼 때 이미 어떤 펫을 골랐는지 알고 있고,
     * 화면에 반영할 다른 값이 없습니다.
     */
    @PatchMapping("/me/default-pet")
    public ResponseEntity<CommonApiResponse<Void>> changeDefaultPet(
            @CurrentUser CustomUserPrincipal principal,
            @RequestBody DefaultPetUpdateRequest request) {

        userProfileService.changeDefaultPet(principal.accountId(), request.petId());
        return ResponseEntity.ok(CommonApiResponse.success(null));
    }
}
