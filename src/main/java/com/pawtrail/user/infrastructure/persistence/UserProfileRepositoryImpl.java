package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.UserProfileJpaRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 *
 * 지금은 그대로 넘기기만 하므로 얇아 보이지만 이 자리가 필요한 이유가 있습니다.
 *   도메인이 스프링 데이터를 직접 알지 않게 됨
 *   조회 방식이 바뀌어도 도메인 인터페이스는 그대로임
 *   조회가 복잡해져도 도메인이 보는 것은 이 클래스 하나임
 *
 * existsIncludingDeleted 가 네이티브 쿼리라는 사실도 이 층에 갇힙니다.
 * 도메인은 "탈퇴한 것까지 포함해 확인한다" 만 알면 됩니다.
 *
 * 조회 수단을 둘 쓰게 되어도 파일은 이것 하나입니다.
 * JpaRepository 와 JPAQueryFactory 를 함께 주입받아,
 * 단순한 조회는 앞의 것에 위임하고 동적 조건은 뒤의 것으로 짭니다.
 *
 * 아직 QueryDSL 을 쓰지 않아 JPAQueryFactory 빈도 만들지 않았습니다.
 * 목록 조회가 생기는 이슈에서 그때 만듭니다.
 */
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository userProfileJpaRepository;

    @Override
    public UserProfile save(UserProfile userProfile) {
        return userProfileJpaRepository.save(userProfile);
    }

    @Override
    public Optional<UserProfile> findById(UUID accountId) {
        return userProfileJpaRepository.findById(accountId);
    }

    @Override
    public boolean existsIncludingDeleted(UUID accountId) {
        return userProfileJpaRepository.existsIncludingDeleted(accountId);
    }
}
