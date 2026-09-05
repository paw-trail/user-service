package com.pawtrail.user.application.dto.input;

/**
 * 프로필 수정 값입니다.
 *
 * 두 필드가 같은 String 타입이라 순서를 바꿔 넘겨도 컴파일이 통과합니다.
 * 그 자리를 막으려고 record 로 묶었습니다.
 *
 * @param nickname        새 닉네임입니다. null 이면 지웁니다.
 * @param profileImageUrl 새 사진 주소입니다. null 이면 지웁니다.
 */
public record ProfileUpdateInput(String nickname, String profileImageUrl) {
}
