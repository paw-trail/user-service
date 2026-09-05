package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.UserProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
