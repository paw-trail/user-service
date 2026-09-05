package com.pawtrail.user.presentation.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawtrail.user.application.dto.input.ProfileUpdateInput;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로필 수정 요청입니다.
 *
 * 사용자가 고칠 수 있는 것은 이 둘뿐입니다.
 * defaultPetId 는 PATCH /users/me/default-pet 이 따로 있고,
 * stats 는 조회할 때 계산하는 값이라 고칠 대상이 아닙니다.
 *
 * 이 클래스만 record 가 아닙니다.
 *
 * PATCH 는 "보낸 것만 바꾼다" 가 계약이라
 * 필드를 아예 안 보낸 것과 null 을 보낸 것을 갈라야 합니다.
 * record 로 받으면 둘 다 null 이 되어 구분할 수 없고,
 * 그러면 사진만 바꾸려고 보낸 요청이 닉네임까지 지웁니다.
 *
 * Jackson 은 JSON 에 그 키가 있을 때만 세터를 부릅니다.
 * 그래서 세터 안에서 플래그를 세우면 별도 라이브러리 없이 세 상태가 갈립니다.
 * Optional 로 받는 방법도 있으나 Jackson 이 "없음" 과 "명시적 null" 을
 * 둘 다 Optional.empty() 로 만들 수 있어 확실하지 않습니다.
 *
 * 세 상태가 이렇게 갈립니다.
 *   키가 없음            provided 가 false        →  그대로 둠
 *   "필드": null        provided 가 true, 값 null →  닉네임은 400, 사진은 지움
 *   "필드": "값"         provided 가 true, 값 있음 →  바꿈
 */
@Getter
@NoArgsConstructor
public class ProfileUpdateRequest {

    // 중복을 허용함
    // 계정 식별은 이메일이 하고 닉네임은 표시용임
    //
    // 길이 2~20 은 auth 의 회원가입 검증과 맞춘 값임
    // 한쪽만 고치면 가입은 되는데 수정이 막히거나 그 반대가 됨
    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
    private String nickname;

    private boolean nicknameProvided;

    // upload-url 로 받은 fileUrl 을 그대로 보냄
    //
    // 길이 규칙을 붙이지 않는 것은 S3 키 설계가 아직 정해지지 않아
    // 상한을 잡을 근거가 없기 때문임, 컬럼도 text 임
    private String profileImageUrl;

    private boolean profileImageUrlProvided;

    @JsonProperty("nickname")
    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameProvided = true;
    }

    @JsonProperty("profileImageUrl")
    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.profileImageUrlProvided = true;
    }

    /**
     * 닉네임을 지우려는 요청을 막습니다.
     *
     * 화면에 "닉네임 지우기" 가 없고, 로컬 가입은 닉네임이 필수라 처음부터 값이 있으며,
     * null 은 소셜 가입 직후에만 있는 임시 상태라 되돌아갈 자리가 아닙니다.
     * 어떤 정상 경로로도 오지 않는 요청이므로 오면 프론트 버그이거나 조작입니다.
     *
     * 지우게 두면 후기 목록의 작성자와 관리자 화면의 제보자 이름이 빈칸이 됩니다.
     * 프론트는 닉네임을 설정하기 전까지 후기 쓰기를 막아 두지만 제보에는 그 잠금이 없습니다.
     *
     * 사진은 대칭이 아닙니다.
     * null 이 "사진 없음" 이라 기본 이미지가 뜨고 화면이 성립하므로 지울 수 있습니다.
     */
    @AssertTrue(message = "닉네임은 지울 수 없습니다")
    public boolean isNicknameNotCleared() {
        return !nicknameProvided || nickname != null;
    }

    /**
     * 서비스가 받는 형태로 바꿉니다.
     *
     * nickname 은 플래그를 넘기지 않습니다.
     * 명시적 null 이 위 검증에서 이미 막히므로 null 은 "안 보냈다" 하나만 뜻합니다.
     *
     * profileImageUrl 은 플래그가 필요합니다.
     * null 이 "지운다" 라는 뜻으로 살아 있기 때문입니다.
     *
     * 계정 식별자는 담지 않습니다.
     * 그 값은 게이트웨이가 넣어 준 헤더에서 오므로 컨트롤러가 따로 넘깁니다.
     */
    public ProfileUpdateInput toInput() {
        return new ProfileUpdateInput(nickname, profileImageUrlProvided, profileImageUrl);
    }
}
