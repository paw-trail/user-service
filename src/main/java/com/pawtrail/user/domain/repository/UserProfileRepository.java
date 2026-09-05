package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.UserProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 프로필을 저장하고 찾아오는 약속입니다.
 *
 * 이 인터페이스에는 JPA 라는 단어가 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 하는지는 infrastructure 가 정합니다.
 *
 * 이슈마다 필요한 것을 여기에 더해 갑니다.
 * 조회 메서드를 미리 만들어도 그 형태가 맞는지는 그 API 를 만들 때 알게 되기 때문입니다.
 */
public interface UserProfileRepository {

    // 새로 만든 프로필을 저장하거나 변경된 프로필을 반영함
    UserProfile save(UserProfile userProfile);

    // 계정 식별자로 찾음
    // 게이트웨이가 넣어 준 X-User-Id 로 조회하는 경로가 여기를 씀
    //
    // 탈퇴한 프로필은 여기에 안 걸림
    // UserProfile 에 @SQLRestriction("deleted_at IS NULL") 이 붙어 있기 때문임
    Optional<UserProfile> findById(UUID accountId);

    // 여러 계정을 한 번에 찾음
    //
    // GET /internal/users?ids= 가 씀
    // review 는 후기 목록의 작성자를, report 는 제보자를 채우는데
    // 목록 한 쪽에 사람이 여럿이라 한 번에 물어봄
    //
    // 없는 식별자는 결과에서 그냥 빠짐, 오류로 보지 않음
    // 부르는 쪽이 자기 목록과 맞춰 쓰므로 빠진 것은 이름 없이 표시하면 됨
    // 탈퇴한 사람도 빠지는데, 그 경우 부르는 쪽이 "탈퇴한 사용자" 로 그림
    List<UserProfile> findAllById(Collection<UUID> accountIds);

    // 탈퇴한 것까지 포함해 그 계정의 행이 있는지 봄
    //
    // 위 findById 로는 삭제 표시 행이 보이지 않으므로 따로 둠
    // account.created 를 소비할 때 "이미 탈퇴한 계정인가" 를 판단하는 데 씀
    //
    // 반환이 Optional 이 아니라 boolean 인 이유
    // 부르는 쪽이 알아야 하는 것은 있는지 없는지뿐이고,
    // 삭제 표시 행은 account_id 말고 담긴 값이 없어 꺼내 봐야 쓸 것이 없음
    boolean existsIncludingDeleted(UUID accountId);
}
