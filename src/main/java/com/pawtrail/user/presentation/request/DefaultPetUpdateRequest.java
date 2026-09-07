package com.pawtrail.user.presentation.request;

import java.util.UUID;

/**
 * 대표 반려동물 지정 요청입니다.
 *
 * petId 가 null 이면 대표를 해제한다는 뜻입니다.
 * 잘못된 요청이 아니라 유효한 요청입니다.
 * 반려동물이 0마리인 상태를 정식으로 지원하기 때문입니다.
 * 한 마리를 키우다 그 아이를 떠나보낸 사람이 프로필에서 지울 수 있어야 합니다.
 *
 * 그 petId 가 실제로 존재하는지, 이 사람 것인지는 여기서 보지 않습니다.
 * pet 서비스를 호출해야 알 수 있는데 아직 그 기반이 없습니다.
 * 검증을 붙이는 것은 외부 호출 기반을 세우는 이슈에서 함께 봅니다.
 *
 * toInput 을 두지 않았습니다.
 * 넘길 값이 petId 하나뿐이라 record 를 하나 더 만들 이유가 없습니다.
 * 필드가 둘 이상 이어지는 자리에만 Input 을 둡니다.
 *
 * @param petId 대표로 지정할 반려동물입니다. null 이면 해제입니다.
 */
public record DefaultPetUpdateRequest(UUID petId) {
}
