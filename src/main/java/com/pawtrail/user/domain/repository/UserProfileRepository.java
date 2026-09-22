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

    // 새 프로필을 INSERT 로만 넣고 그 자리에서 데이터베이스로 내보냄
    //
    // save 와 따로 둔 이유
    // 기본 키를 이벤트나 요청이 주는 값으로 채우므로 save 는 merge 로 돎
    // merge 는 같은 기본 키의 살아 있는 행이 그 사이 생겼으면 오류 없이 그 행을 덮어씀
    // 자가 복구와 가입 이벤트가 거의 같은 순간에 만들면 한쪽이 다른 쪽 닉네임을 null 로 지울 수 있음
    // INSERT 로만 넣으면 그때 기본 키 충돌로 실패해 부르는 쪽이 알 수 있음
    //
    // 그 자리에서 내보내는 이유
    // 커밋 시점까지 미루면 충돌 예외가 트랜잭션을 되돌리는 과정에서 나 부르는 쪽이 가리기 어려움
    //
    // 충돌하면 DataIntegrityViolationException 이 남
    UserProfile create(UserProfile userProfile);

    // 탈퇴한 것까지 포함해 그 계정의 프로필을 찾음
    //
    // 탈퇴 처리 · 가입 이벤트 소비 · GET /users/me 가 씀
    // 프로필이 놓일 수 있는 상태가 셋인데 조회 한 번으로 갈리게 하려고 둠
    //   비어 있음             행이 아예 없음
    //   isDeleted 가 true    탈퇴 표시가 있음
    //   그 밖                 살아 있는 프로필
    // 셋을 받아 무엇을 할지는 부르는 쪽마다 다름
    //   탈퇴 처리         삭제 표시 행을 만듦 · 그대로 둠 · 익명화
    //   가입 이벤트 소비   만듦 · 건너뜀 · 비어 있는 닉네임을 채움
    //   GET /users/me    자가 복구 · 404 · 그대로 돌려줌
    //
    // 위 findById 로는 앞의 둘이 똑같이 비어 있는 Optional 로 보임
    //
    // 돌려받은 엔티티는 영속 상태라 값을 고치면 커밋 시점에 반영됨
    Optional<UserProfile> findByIdIncludingDeleted(UUID accountId);
}
