package com.pawtrail.user.application.dto.output;

import java.util.UUID;

/**
 * 마이페이지가 받는 프로필입니다.
 *
 * stats 는 이 표에 없는 값이라 서비스가 따로 세어 넘깁니다.
 * 비정규화 컬럼을 두지 않는 이유는, 후기를 지웠을 때 user 가 그것을 알 방법이 없어
 * 값이 조용히 틀어지기 때문입니다.
 *
 * 사진은 저장된 키가 아니라 서명된 주소로 나갑니다.
 * 버킷이 퍼블릭 액세스를 차단해 두어 서명 없이는 열리지 않기 때문입니다.
 * 조립은 서비스가 하므로 여기에 from 이나 of 를 두지 않습니다.
 * 엔티티만으로는 만들 수 없는 값이 둘(사진 주소, stats)이라
 * 팩터리를 두면 그것들을 인자로 또 받아야 합니다.
 *
 * 이메일은 담지 않습니다.
 * auth 가 소유한 값이라 프론트가 필요하면 GET /auth/me 를 따로 부릅니다.
 *
 * @param accountId       계정 식별자입니다. 이 표의 기본 키이기도 합니다.
 * @param nickname        소셜 가입 직후에는 null 입니다.
 * @param profileImageUrl 안 올렸으면 null 입니다.
 * @param defaultPetId    펫이 0마리이거나 대표를 해제했으면 null 입니다.
 * @param stats           방문·후기·즐겨찾기 수입니다.
 */
public record ProfileOutput(UUID accountId,
                            String nickname,
                            String profileImageUrl,
                            UUID defaultPetId,
                            Stats stats) {

    /**
     * 마이페이지 상단의 숫자 셋입니다.
     *
     * reviewCount 만 null 이 될 수 있습니다.
     * 나머지 둘은 우리 표를 세는 값이라 실패할 자리가 없습니다.
     *
     * @param visitCount    visit_log 의 행 수입니다.
     * @param reviewCount   review 서비스가 주는 값입니다.
     *                      그 서비스가 아직 없어 지금은 항상 null 입니다.
     *                      명세도 "호출 실패 시 null" 로 정해 두었으므로 프론트는 이 상태를 다룹니다.
     *                      통계 하나 때문에 마이페이지가 안 뜨면 안 됩니다.
     * @param favoriteCount favorite 의 행 수입니다.
     */
    public record Stats(long visitCount, Long reviewCount, long favoriteCount) {
    }
}
