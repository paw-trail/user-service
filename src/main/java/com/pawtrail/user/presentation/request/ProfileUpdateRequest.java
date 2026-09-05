package com.pawtrail.user.presentation.request;

import com.pawtrail.user.application.dto.input.ProfileUpdateInput;
import jakarta.validation.constraints.Size;

/**
 * 프로필 수정 요청입니다.
 *
 * 사용자가 고칠 수 있는 것은 이 둘뿐입니다.
 * defaultPetId 는 PATCH /users/me/default-pet 이 따로 있고,
 * stats 는 조회할 때 계산하는 값이라 고칠 대상이 아닙니다.
 *
 * 두 필드 모두 선택입니다.
 * 보내지 않으면 그대로 두고, null 을 보내면 지웁니다.
 * 그래서 이 record 만으로는 "안 보냈다" 와 "null 을 보냈다" 를 구분할 수 없는데,
 * 여기서는 구분할 필요가 없습니다.
 * 둘 다 null 로 들어오고 서비스가 null 을 그대로 반영하므로
 * 값을 안 보낸 필드도 지워집니다. 화면이 두 값을 항상 함께 보내기 때문입니다.
 *
 * @param nickname        중복을 허용합니다. 계정 식별은 이메일이 하고 닉네임은 표시용입니다.
 *                        길이 2~20 은 auth 의 회원가입 검증과 맞춘 값입니다.
 *                        한쪽만 고치면 가입은 되는데 수정이 막히거나 그 반대가 됩니다.
 * @param profileImageUrl upload-url 로 받은 fileUrl 을 그대로 보냅니다.
 *                        길이 규칙을 붙이지 않는 것은 S3 키 설계가 아직 정해지지 않아
 *                        상한을 잡을 근거가 없기 때문입니다. 컬럼도 text 입니다.
 */
public record ProfileUpdateRequest(

        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
        String nickname,

        String profileImageUrl
) {

    /**
     * 서비스가 받는 형태로 바꿉니다.
     *
     * 계정 식별자는 담지 않습니다.
     * 그 값은 게이트웨이가 넣어 준 헤더에서 오므로 컨트롤러가 따로 넘깁니다.
     */
    public ProfileUpdateInput toInput() {
        return new ProfileUpdateInput(nickname, profileImageUrl);
    }
}
