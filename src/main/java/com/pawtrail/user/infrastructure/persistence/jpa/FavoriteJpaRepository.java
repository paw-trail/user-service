package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.Favorite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 */
public interface FavoriteJpaRepository extends JpaRepository<Favorite, UUID> {

    long countByAccountId(UUID accountId);

    List<Favorite> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);

    Optional<Favorite> findByAccountIdAndPlaceId(UUID accountId, UUID placeId);

    /**
     * 그 장소를 담아 둔 사람들의 계정 식별자만 돌려줍니다.
     *
     * 파생 쿼리로는 특정 컬럼만 고를 수 없어 JPQL 을 씁니다.
     * 엔티티를 통째로 읽어 와서 자바에서 꺼내면
     * 쓰지 않는 컬럼까지 실어 오고 페이징 카운트 쿼리도 무거워집니다.
     *
     * 정렬을 걸지 않습니다.
     * 알림 대상자 목록이라 순서에 뜻이 없고,
     * 부르는 쪽이 페이지를 돌며 전부 처리합니다.
     *
     * @Param 을 붙이는 이유
     * 이름으로 바인딩하려면 컴파일 결과에 파라미터 이름이 남아 있어야 합니다.
     * 부트 Gradle 플러그인이 -parameters 를 켜 주기는 하지만
     * 그 설정에 기대면 빌드 설정이 바뀔 때 조용히 깨집니다.
     */
    @Query("select f.accountId from Favorite f where f.placeId = :placeId")
    Page<UUID> findAccountIdsByPlaceId(@Param("placeId") UUID placeId, Pageable pageable);
}
