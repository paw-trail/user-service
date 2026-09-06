package com.pawtrail.user.domain.provider;

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
}
