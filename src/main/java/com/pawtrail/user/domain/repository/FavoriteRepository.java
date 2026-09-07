package com.pawtrail.user.domain.repository;

import com.pawtrail.user.domain.model.Favorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 즐겨찾기를 저장하고 찾아오는 약속입니다.
 *
 * 하드 딜리트이므로 지울 때 delete 를 씁니다.
 * 소프트 딜리트인 UserProfile 과 다른 점입니다.
 *
 * 이 인터페이스에는 JPA 라는 단어가 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 하는지는 infrastructure 가 정합니다.
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

    // 그 사람의 즐겨찾기를 최근에 담은 것부터 전부 돌려줌
    //
    // GET /api/v1/favorites 가 씀
    //
    // 페이징하지 않는 이유
    // 카테고리 칩을 프론트가 응답 전체를 placeType 으로 세어 만들고
    // 0건인 칩은 그리지 않기로 되어 있음
    // 페이지로 자르면 칩 개수와 필터가 페이지마다 갈려 화면 규칙이 깨짐
    //
    // 정렬이 created_at DESC 인 이유
    // 정렬을 안 걸면 새로고침할 때마다 카드 순서가 바뀜
    // 방금 담은 곳이 가장 관심 있는 곳이므로 위로 올림
    List<Favorite> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);

    // 그 사람이 그 장소를 담아 두었는지 찾음
    //
    // DELETE /api/v1/favorites/{placeId} 가 씀
    // 해제는 favoriteId 가 아니라 placeId 로 받으므로 이 조회가 필요함
    // 하트를 누르는 자리가 장소 카드라 프론트가 아는 값이 placeId 뿐임
    //
    // 없으면 비어 있는 Optional 이 돌아옴
    // 해제는 멱등이라 부르는 쪽이 그것을 오류로 보지 않음
    Optional<Favorite> findByAccountIdAndPlaceId(UUID accountId, UUID placeId);

    // 그 장소를 담아 둔 사람들의 계정 식별자를 돌려줌
    //
    // GET /internal/favorites?placeId= 가 씀
    // 조건이 바뀌었을 때 notification 이 알릴 대상자를 찾는 경로임
    //
    // 페이징하는 이유
    // 위 목록 조회와 성격이 반대임
    // 그쪽은 한 사람이 모은 것이라 건수에 자연스러운 상한이 있지만
    // 이쪽은 한 장소에 몰린 사람 수라 인기 장소면 수천 명일 수 있음
    // 브라우저가 보는 화면이 아니라 칩 규칙도 걸리지 않음
    //
    // 엔티티가 아니라 UUID 만 돌려주는 이유
    // 부르는 쪽이 하는 일이 "이 사람들에게 알림을 만든다" 뿐이고
    // 담은 시각이나 메모는 알림 문구에도 수신 설정 판단에도 쓰이지 않음
    //
    // 순서가 요청 사이에 흔들리지 않음
    // 페이지를 나눠 가져가는데 순서가 바뀌면
    // 같은 계정이 두 페이지에 나오거나 아예 빠져 알림이 두 번 가거나 안 감
    Page<UUID> findAccountIdsByPlaceId(UUID placeId, Pageable pageable);

    // 주어진 장소 중 그 사람이 담아 둔 것의 place_id 만 돌려줌
    //
    // GET /api/v1/visits 가 씀
    // 방문 기록 카드의 하트를 채울지 비울지 판단하는 값(isFavorite)이 이것임
    //
    // 즐겨찾기 화면에는 이 조회가 필요 없음
    // 거기는 하트가 해제만 하므로 목록에 있다는 것이 곧 담겨 있다는 뜻임
    // 방문 기록은 하트가 추가와 해제를 다 해서 처음 그릴 때 상태를 알아야 함
    //
    // 장소마다 부르지 않고 한 번에 모아 부름
    // uq_favorite_account_place 가 (account_id, place_id) 라 이 조회가 그 인덱스를 그대로 탐
    //
    // 엔티티가 아니라 place_id 만 돌려주는 이유
    // 부르는 쪽이 하는 일이 contains 하나뿐임
    Set<UUID> findPlaceIdsByAccountIdAndPlaceIdIn(UUID accountId, Collection<UUID> placeIds);
    // 그 계정의 즐겨찾기 을 한 번에 지움
    //
    // 탈퇴 처리가 씀
    // 반환은 지운 행 수임, 이벤트 소비는 응답이 없어 로그가 유일한 흔적이라 남겨 둠
    int deleteAllByAccountId(UUID accountId);
}
