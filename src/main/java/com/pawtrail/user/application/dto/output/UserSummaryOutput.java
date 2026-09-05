package com.pawtrail.user.application.dto.output;

import com.pawtrail.user.domain.model.UserProfile;
import java.util.UUID;

/**
 * 다른 서비스가 받아 가는 사용자 요약입니다.
 *
 * review 가 후기 목록의 작성자 이름을, report 가 제보자 이름을 채울 때 씁니다.
 * 둘 다 여러 사람을 한 번에 물어보므로 배치 조회입니다.
 *
 * 담는 값이 둘뿐인 것은 부르는 쪽이 그 둘만 쓰기 때문입니다.
 * defaultPetId 나 stats 를 함께 주면 쓰지도 않는 값이 서비스 경계를 넘어갑니다.
 *
 * @param accountId       누구인지입니다. 부르는 쪽이 이 값으로 자기 목록과 맞춥니다.
 * @param nickname        탈퇴한 사람은 "탈퇴한 사용자" 로 치환된 값이 나갑니다.
 *                        아직 설정하지 않은 사람은 null 입니다.
 * @param profileImageUrl 안 올렸거나 탈퇴했으면 null 입니다.
 */
public record UserSummaryOutput(UUID accountId,
                                String nickname,
                                String profileImageUrl) {

    public static UserSummaryOutput from(UserProfile profile) {
        return new UserSummaryOutput(
                profile.getAccountId(),
                profile.getNickname(),
                profile.getProfileImageUrl());
    }
}
