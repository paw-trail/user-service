package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.Favorite;
import java.util.Optional;
import java.util.UUID;

/**
 * 즐겨찾기를 저장하고 찾아오는 약속입니다.
 *
 * 지금은 최소한만 둡니다.
 * 목록 조회와 장소별 대상자 조회는 그 API 를 만드는 이슈에서 더합니다.
 *
 * 하드 딜리트이므로 지울 때 delete 를 씁니다.
 * 소프트 딜리트인 UserProfile 과 다른 점입니다.
 */
public interface FavoriteRepository {

    Favorite save(Favorite favorite);

    Optional<Favorite> findById(UUID id);

    void delete(Favorite favorite);
}
