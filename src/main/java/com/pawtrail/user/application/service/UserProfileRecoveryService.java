package com.pawtrail.user.application.service;

import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 없는 프로필을 새 트랜잭션에서 만듭니다.
 *
 * GET /users/me 의 자가 복구가 부릅니다.
 * 별도 빈인 이유는 트랜잭션 경계 때문입니다.
 *
 * 조회 트랜잭션 안에서 만들다 기본 키가 부딪히면 그 예외가 트랜잭션에 rollback-only 를 남깁니다.
 * 예외를 잡아도 표시가 지워지지 않아, 메서드가 정상으로 끝나도 커밋이 거부됩니다.
 * 새 트랜잭션으로 떼면 되돌려지는 것은 이쪽뿐이고 부르는 쪽은 물들지 않아,
 * 예외를 잡고 먼저 생긴 행을 다시 읽을 수 있습니다.
 * place 의 PlacePendingUpdateService 와 auth 의 TokenRevokeService 가 같은 이유로 떼어 두었습니다.
 *
 * 자기 호출로는 프록시를 타지 않으므로 반드시 별도 빈이어야 합니다.
 */
@Service
@RequiredArgsConstructor
public class UserProfileRecoveryService {

    private final UserProfileRepository userProfileRepository;

    /**
     * 닉네임 없이 프로필을 만듭니다.
     *
     * 닉네임은 account.created 에만 있어 여기서는 알 수 없습니다.
     * 이 컬럼의 null 은 "아직 설정 안 함" 이라, 늦게 온 이벤트가 채우거나 사용자가 직접 정합니다.
     *
     * save 가 아니라 create 로 INSERT 만 합니다.
     * 그 사이 account.created 소비가 같은 행을 만들었으면 merge 는 그 행의 닉네임을 null 로 덮지만,
     * INSERT 는 기본 키 충돌로 실패해 부르는 쪽이 먼저 생긴 행을 읽게 됩니다.
     *
     * 예외를 여기서 잡지 않습니다.
     * 잡고 정상으로 돌아가면 이 트랜잭션을 커밋하려다 UnexpectedRollbackException 이 나고,
     * 그 예외는 부르는 쪽이 가릴 수 없습니다.
     * 그대로 내보내면 스프링이 이 트랜잭션만 되돌리고 원래 예외를 다시 던집니다.
     *
     * @param accountId 게이트웨이가 토큰에서 꺼내 넣어 준 계정 식별자입니다.
     * @return 만든 프로필입니다. 이 트랜잭션이 끝나면 영속성 컨텍스트에서 떨어지므로 읽기만 합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserProfile createEmpty(UUID accountId) {
        return userProfileRepository.create(UserProfile.create(accountId, null));
    }
}
