package com.pawtrail.user.domain.provider;

import com.pawtrail.user.domain.provider.dto.PlaceData;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * place 서비스에서 장소를 받아오는 약속입니다.
 *
 * 이 인터페이스에 HTTP 도 RestClient 도 나오지 않습니다.
 * 무엇을 받아오는지만 적고 어떻게 받아오는지는 infrastructure 가 정합니다.
 */
public interface PlaceProvider {

    /**
     * 여러 장소를 한 번에 받아옵니다.
     *
     * 즐겨찾기와 방문 기록과 일정이 모두 이 메서드를 씁니다.
     * 목록 한 쪽에 장소가 여럿이라 한 건씩 물어보면 요청이 그 수만큼 늘어납니다.
     *
     * 결과를 Map 으로 돌려주는 데 뜻이 있습니다.
     * 부르는 쪽이 자기 목록을 돌며 placeId 로 찾아 카드를 조립하는데,
     * 그때 키가 없는 것이 곧 "그 장소가 사라졌다" 는 뜻이 됩니다.
     *
     * 없는 식별자는 결과에서 그냥 빠집니다. 오류로 보지 않습니다.
     * 장소는 폐업해도 행이 남지만, 관리자가 잘못 묶인 소스를 분리하거나
     * 재수집 중 두 장소가 하나로 합쳐지면 placeId 자체가 없어질 수 있습니다.
     * 담을 때 검사해도 담은 뒤에 사라지는 경우는 막지 못하므로
     * 걸러내는 자리를 조회 한 곳으로 모았습니다.
     *
     * 호출이 실패하면 null 을 돌려줍니다. 예외를 던지지 않습니다.
     *
     * 빈 Map 이 아니라 null 인 것은 둘을 갈라야 하기 때문입니다.
     *
     *   빈 Map    물어봤는데 남아 있는 장소가 하나도 없었다
     *   null      물어보지 못했다
     *
     * 부르는 쪽의 할 일이 정반대입니다.
     * 앞엣것은 빈 목록을 그대로 내려보내면 되지만,
     * 뒤엣것은 장소 이름이 없어 카드가 성립하지 않으므로 요청 자체를 실패시킵니다.
     * 판정이나 평점과 달리 없으면 화면의 목적이 이뤄지지 않기 때문입니다.
     */
    Map<UUID, PlaceData> findByIds(Collection<UUID> placeIds);
}
