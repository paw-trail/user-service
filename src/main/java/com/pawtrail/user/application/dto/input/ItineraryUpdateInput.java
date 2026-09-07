package com.pawtrail.user.application.dto.input;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 담아 둔 일정을 고칠 때 서비스가 받는 값입니다.
 *
 * 세 필드를 다루는 방식이 다릅니다.
 *
 * visitAt 은 null 이면 "안 보냈다" 하나만 뜻합니다.
 * 컬럼이 NOT NULL 이라 지울 수 없고, 명시적 null 은 요청 계층의 검증이 이미 막습니다.
 *
 * petId 와 memo 는 null 이 "비운다" 라는 뜻으로 살아 있어
 * "안 보냈다" 와 가르려면 플래그가 따로 필요합니다.
 * 프로필 수정이 profileImageUrl 에 같은 구조를 쓰고 있습니다.
 *
 * @param visitAt      새 방문 예정 일시입니다. null 이면 건드리지 않습니다.
 * @param petIdProvided 동반 동물 필드가 요청에 있었는지입니다.
 * @param petId        새 동반 동물입니다. 위 값이 true 이고 이것이 null 이면 뗍니다.
 * @param memoProvided 메모 필드가 요청에 있었는지입니다.
 * @param memo         새 메모입니다. 위 값이 true 이고 이것이 null 이면 지웁니다.
 */
public record ItineraryUpdateInput(LocalDateTime visitAt,
                                   boolean petIdProvided,
                                   UUID petId,
                                   boolean memoProvided,
                                   String memo) {
}
