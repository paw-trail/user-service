package com.pawtrail.user.application.dto.input;

/**
 * 프로필 수정 값입니다.
 *
 * 두 필드를 다루는 방식이 다릅니다.
 *
 * nickname 은 null 이면 "안 보냈다" 하나만 뜻합니다.
 * 명시적 null 은 요청 계층의 검증이 이미 막았기 때문입니다.
 *
 * profileImageUrl 은 null 이 "지운다" 라는 뜻으로 살아 있어
 * "안 보냈다" 와 가르려면 플래그가 따로 필요합니다.
 *
 * @param nickname                새 닉네임입니다. null 이면 건드리지 않습니다.
 * @param profileImageUrlProvided 사진 필드가 요청에 있었는지입니다.
 * @param profileImageUrl         새 사진 주소입니다. 위 값이 true 이고 이것이 null 이면 지웁니다.
 */
public record ProfileUpdateInput(String nickname,
                                 boolean profileImageUrlProvided,
                                 String profileImageUrl) {
}
