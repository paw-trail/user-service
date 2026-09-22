package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.UserProfileJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.List;
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
 * findByIdIncludingDeleted 가 네이티브 쿼리라는 사실과
 * create 가 EntityManager 로 곧장 INSERT 한다는 사실도 이 층에 갇힙니다.
 * 도메인은 "탈퇴한 것까지 포함해 찾는다" · "새로 넣는다" 만 알면 됩니다.
 *
 * 아직 QueryDSL 을 쓰지 않아 JPAQueryFactory 빈도 만들지 않았습니다.
 * 동적 조건이 붙는 목록 조회가 생기는 이슈에서 그때 만듭니다.
 */
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository userProfileJpaRepository;

    // create 가 씀
    // 스프링 데이터의 save 는 기본 키가 채워진 엔티티를 merge 로 넣어 여기서는 쓸 수 없음
    // final 이 아니라 생성자 주입에서 빠지고, 컨테이너가 필드에 공유 EntityManager 를 넣음
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public UserProfile save(UserProfile userProfile) {
        return userProfileJpaRepository.save(userProfile);
    }

    // persist 는 INSERT 만 함 — 같은 기본 키의 행이 있으면 내보낼 때 충돌로 실패함
    // flush 로 그 자리에서 내보내 충돌을 이 호출 안에서 드러냄
    // 이 클래스가 @Repository 라 하이버네이트 예외가 DataIntegrityViolationException 으로 바뀌어 나감
    @Override
    public UserProfile create(UserProfile userProfile) {
        entityManager.persist(userProfile);
        entityManager.flush();
        return userProfile;
    }

    @Override
    public Optional<UserProfile> findById(UUID accountId) {
        return userProfileJpaRepository.findById(accountId);
    }

    @Override
    public List<UserProfile> findAllById(Collection<UUID> accountIds) {
        return userProfileJpaRepository.findAllById(accountIds);
    }

    @Override
    public Optional<UserProfile> findByIdIncludingDeleted(UUID accountId) {
        return userProfileJpaRepository.findByIdIncludingDeleted(accountId);
    }
}
