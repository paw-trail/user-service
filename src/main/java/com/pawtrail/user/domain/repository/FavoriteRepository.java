package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.Favorite;
import java.util.Optional;
import java.util.UUID;

/**
 * 즐겨찾기를 저장하고 찾아오는 약속입니다.
 *
 * 하드 딜리트이므로 지울 때 delete 를 씁니다.
 * 소프트 딜리트인 UserProfile 과 다른 점입니다.
 */
public interface FavoriteRepository {

    Favorite save(Favorite favorite);

    Optional<Favorite> findById(UUID id);

    void delete(Favorite favorite);

    // 그 사람의 즐겨찾기 수를 셈
    // 마이페이지 상단의 stats.favoriteCount 가 이 값임
    //
    // 비정규화 컬럼을 두지 않고 셀 때마다 세는 이유
    // 컬럼을 두면 담고 지울 때마다 두 곳을 함께 고쳐야 하고,
    // 한쪽만 실패하면 숫자가 조용히 틀어짐
    long countByAccountId(UUID accountId);
}
