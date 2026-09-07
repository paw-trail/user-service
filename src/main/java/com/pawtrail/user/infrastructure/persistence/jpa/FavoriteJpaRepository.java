package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.Favorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * 기본 키로 정렬합니다.
     *
     * 알림 대상자 목록이라 어떤 순서로 오든 뜻은 같지만,
     * 페이지를 나눠 가져가려면 요청 사이에 순서가 흔들리지 않아야 합니다.
     * order by 가 없으면 데이터베이스가 순서를 보장하지 않고
     * 같은 조건이라도 실행 계획에 따라 행이 다르게 나올 수 있어,
     * 1페이지에 나온 계정이 2페이지에 또 나오거나 아예 빠질 수 있습니다.
     * 그러면 알림이 두 번 가거나 받아야 할 사람이 못 받습니다.
     *
     * created_at 이 아니라 id 로 정렬하는 이유는 유일하기 때문입니다.
     * 같은 시각에 담은 행이 여럿이면 created_at 만으로는 순서가 정해지지 않습니다.
     * id 는 UUID v7 이라 유일하면서 시간순이기도 해 정렬 기준으로 그대로 맞습니다.
     *
     * @Param 을 붙이는 이유
     * 이름으로 바인딩하려면 컴파일 결과에 파라미터 이름이 남아 있어야 합니다.
     * 부트 Gradle 플러그인이 -parameters 를 켜 주기는 하지만
     * 그 설정에 기대면 빌드 설정이 바뀔 때 조용히 깨집니다.
     */
    @Query("select f.accountId from Favorite f where f.placeId = :placeId order by f.id")
    Page<UUID> findAccountIdsByPlaceId(@Param("placeId") UUID placeId, Pageable pageable);

    /**
     * 주어진 장소 중 그 사람이 담아 둔 것의 place_id 만 돌려줍니다.
     *
     * 파생 쿼리로는 특정 컬럼만 고를 수 없어 JPQL 을 씁니다.
     *
     * 정렬을 걸지 않습니다.
     * 부르는 쪽이 Set 으로 받아 contains 로만 쓰므로 순서에 뜻이 없습니다.
     * 페이징도 하지 않아 순서가 흔들려 생기는 문제도 없습니다.
     */
    @Query("""
            select f.placeId from Favorite f
            where f.accountId = :accountId and f.placeId in :placeIds
            """)
    Set<UUID> findPlaceIdsByAccountIdAndPlaceIdIn(
            @Param("accountId") UUID accountId, @Param("placeIds") Collection<UUID> placeIds);
}
