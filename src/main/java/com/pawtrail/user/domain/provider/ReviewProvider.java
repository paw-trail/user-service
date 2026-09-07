package com.pawtrail.user.domain.provider;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * review 서비스에서 값을 받아오는 약속입니다.
 *
 * 이 인터페이스에 HTTP 도 RestClient 도 나오지 않습니다.
 * 무엇을 받아오는지만 적고 어떻게 받아오는지는 infrastructure 가 정합니다.
 *
 * StorageProvider 와 같은 자리에 두지만 성격이 하나 다릅니다.
 * 그쪽은 바깥 시스템(S3)이고 이쪽은 우리가 만든 다른 서비스입니다.
 * 그래서 구현이 external 이 아니라 internal 아래에 놓입니다.
 *
 * 메서드 둘의 기준이 다릅니다.
 * 하나는 계정 기준이고 하나는 장소 기준입니다.
 * 부르는 서비스가 같아 한 인터페이스에 두었습니다.
 */
public interface ReviewProvider {

    /**
     * 그 사람이 쓴 후기가 몇 건인지 알려줍니다.
     *
     * 마이페이지의 stats 에 들어갑니다.
     * 셋 중 둘은 우리 표를 세고 이 값만 review 서비스가 줍니다.
     *
     * 받아오지 못하면 null 을 돌려줍니다. 예외를 던지지 않습니다.
     * 통계 하나 때문에 마이페이지 전체가 안 뜨면 안 되기 때문입니다.
     * 명세도 "호출 실패 시 null" 로 정해 두었고 프론트가 그 상태를 다룹니다.
     *
     * 실패를 여기서 삼키는 것은 이 값에 한정된 판단입니다.
     * 방문 기록의 판정처럼 틀린 값이 영구히 남는 자리에서는 반대로 요청을 실패시킵니다.
     * 그래서 실패 처리를 공통 모듈에 두지 않고 부르는 쪽마다 정합니다.
     */
    Long countByAccountId(UUID accountId);

    /**
     * 여러 장소의 평점 평균을 한 번에 받아옵니다.
     *
     * 즐겨찾기 카드의 별점이 이 값입니다.
     * 평점은 review 가 소유합니다.
     * place 표에는 평점 컬럼이 아예 없고, 검색 색인이 가진 값은 하루 한 번 동기화하는
     * 사본이라 목록에서 그것을 읽으면 우리가 남의 캐시를 들여다보는 모양이 됩니다.
     *
     * 후기가 하나도 없는 장소는 결과에서 빠집니다.
     * 평균을 낼 것이 없어 0 도 아니고 없는 것이며,
     * 부르는 쪽은 키가 없으면 별점을 표시하지 않습니다.
     *
     * 호출이 실패하면 빈 Map 을 돌려줍니다. 예외를 던지지 않습니다.
     * 별점은 없어도 카드가 성립합니다. countByAccountId 와 같은 기준입니다.
     */
    Map<UUID, Double> findRatingsByPlaceIds(Collection<UUID> placeIds);
}
