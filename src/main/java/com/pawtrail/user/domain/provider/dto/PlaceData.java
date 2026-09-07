package com.pawtrail.user.domain.provider.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * place 서비스에서 받아온 장소 하나입니다.
 *
 * 도메인이 쓰는 형태이며 place 의 응답 규격이 아닙니다.
 * 그쪽 규격은 infrastructure 의 PlaceResponse 가 받고,
 * PlaceProviderImpl 이 이 타입으로 바꿔 넘깁니다.
 *
 * 둘을 나눈 이유는 의존 방향입니다.
 * 도메인이 infrastructure 의 타입을 반환값으로 쓰면 안쪽이 바깥을 알게 됩니다.
 * 나누어 두면 place 가 필드를 더하거나 이름을 바꿔도 바뀌는 곳이 구현 한 곳입니다.
 *
 * 필드가 여섯인 것은 카드에 필요한 것만 받기로 했기 때문입니다.
 * 좌표는 지금 카드에 쓰지 않지만 일정의 여정 지도가 마커와 경로선을 그리므로
 * 확실히 쓸 자리가 있어 미리 받아 둡니다.
 * 주소는 화면에도 명세 응답에도 없어 받지 않습니다.
 *
 * 좌표를 BigDecimal 로 두는 것은 place 의 컬럼이 numeric(10,7) 이기 때문입니다.
 * double 로 받으면 소수점 아래에서 값이 미세하게 달라집니다.
 */
public record PlaceData(
        UUID placeId,
        String name,
        String placeType,
        String imageUrl,
        BigDecimal lat,
        BigDecimal lon) {
}
