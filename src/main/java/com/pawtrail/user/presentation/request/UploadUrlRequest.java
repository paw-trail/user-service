package com.pawtrail.user.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 업로드 주소 발급 요청입니다.
 *
 * 파일 이름을 받지 않습니다.
 * 명세의 POST /pets/upload-url 은 fileName 을 받지만 그쪽은 한 사람이 여러 마리라
 * 이름으로 갈라야 합니다.
 * 프로필 사진은 계정당 하나이고 키가 users/{accountId}/profile 로 고정이라
 * 이름을 쓸 자리가 없습니다.
 *
 * 파일 자체도 받지 않습니다.
 * 이 API 는 주소만 발급하고 브라우저가 S3 로 직접 PUT 합니다.
 * 이름이 uploads 가 아니라 upload-url 인 이유가 그것입니다.
 *
 * @param contentType 올릴 파일의 형식입니다. image/jpeg 또는 image/png 입니다.
 *                    서명에 들어가므로 브라우저는 이 타입으로만 올릴 수 있고,
 *                    S3 가 그 값을 객체에 저장해 조회할 때 그대로 돌려줍니다.
 *                    확장자를 안 붙이는데도 브라우저가 형식을 아는 것이 이 때문입니다.
 */
public record UploadUrlRequest(

        // 허용할 형식을 여기서 못 박음
        //
        // 서명에 실려 나가는 값이라 걸러 두지 않으면
        // 사용자가 아무 타입이나 지정해 올릴 수 있음
        // 예를 들어 text/html 로 올리면 그 주소를 열었을 때 브라우저가 문서로 해석함
        //
        // 크기 제한은 여기서 못 함
        // 파일이 서버를 거치지 않으므로 서버가 볼 수 있는 것이 없음
        // 필요해지면 presign 에 조건을 걸거나 업로드 후 확인하는 방식이 됨
        @NotBlank(message = "contentType 을 보내 주세요")
        @Pattern(regexp = "image/(jpeg|png)",
                message = "image/jpeg 또는 image/png 만 올릴 수 있습니다")
        String contentType
) {
}
