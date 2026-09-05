package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.UserProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 *
 * 이 파일은 도메인이 보지 않습니다.
 *
 * persistence 바로 아래가 아니라 jpa 하위에 두는 것은 층이 다르기 때문입니다.
 * UserProfileRepositoryImpl 은 도메인이 선언한 약속의 구현이지만,
 * 이 인터페이스는 그 구현이 쓰는 부품입니다.
 *
 * 식별자 타입이 UUID 인 것은 기본 키가 account_id 이기 때문입니다.
 * 이 표만 대리 키 id 가 없습니다.
 */
public interface UserProfileJpaRepository extends JpaRepository<UserProfile, UUID> {

    /**
     * 탈퇴한 것까지 포함해 행의 존재만 확인합니다.
     *
     * 네이티브 쿼리인 이유는 @SQLRestriction 을 우회해야 하기 때문입니다.
     * 그 애노테이션은 하이버네이트가 만드는 모든 조회에 deleted_at IS NULL 을 덧붙이므로
     * existsById 로는 삭제 표시 행이 보이지 않습니다.
     * 네이티브 쿼리는 하이버네이트가 문장을 만들지 않아 그 제한을 받지 않습니다.
     *
     * SELECT EXISTS 를 쓰면 PostgreSQL 이 첫 행을 찾는 즉시 멈춥니다.
     * COUNT 는 조건에 맞는 행을 끝까지 세는데 여기서는 그럴 이유가 없습니다.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM user_profile WHERE account_id = :accountId)",
            nativeQuery = true)
    boolean existsIncludingDeleted(@Param("accountId") UUID accountId);
}
