package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.UserProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * 프로필을 저장하고 찾아오는 약속입니다.
 *
 * 이 인터페이스에는 JPA 라는 단어가 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 하는지는 infrastructure 가 정합니다.
 *
 * 지금은 최소한만 둡니다.
 * 조회 메서드를 미리 만들어도 그 형태가 맞는지는 그 API 를 만들 때 알게 되므로,
 * 이슈마다 필요한 것을 여기에 더해 갑니다.
 *
 * 탈퇴한 프로필은 엔티티의 @SQLRestriction 이 걸러 냅니다.
 * 그 제한을 우회해야 하는 조회는 이벤트 순서 역전을 막을 때 필요한데,
 * 그것은 account.created 를 소비하는 이슈에서 더합니다.
 */
public interface UserProfileRepository {

    // 새로 만든 프로필을 저장하거나 변경된 프로필을 반영함
    UserProfile save(UserProfile userProfile);

    // 계정 식별자로 찾음
    // 게이트웨이가 넣어 준 X-User-Id 로 조회하는 경로가 여기를 씀
    Optional<UserProfile> findById(UUID accountId);
}
