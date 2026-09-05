package com.pawtrail.user.infrastructure.provider.external;

import com.pawtrail.user.domain.provider.StorageProvider;
import com.pawtrail.user.infrastructure.config.StorageProperties;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 도메인이 선언한 약속을 S3 로 구현합니다.
 *
 * external 아래에 두는 것은 우리가 만들지 않은 바깥 시스템이기 때문입니다.
 * internal 은 같은 프로젝트의 다른 서비스를 부르는 자리입니다.
 *
 * 서명을 만드는 일은 통신이 아닙니다.
 * 액세스 키로 문자열에 서명하는 계산이라 S3 를 부르지 않고, 그래서 빠르고 실패하지 않습니다.
 * 실제 요청은 그 주소를 받은 브라우저가 보냅니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3StorageProvider implements StorageProvider {

    private static final String PROFILE_KEY_FORMAT = "users/%s/profile";

    // 가상 호스팅 방식 주소임
    // 버킷 이름이 호스트 앞에 붙는 형태이고 지금 S3 의 기본임
    // 경로 방식(s3.리전.amazonaws.com/버킷/키)은 옛 방식이라 쓰지 않음
    private static final String PUBLIC_URL_FORMAT = "https://%s.s3.%s.amazonaws.com/%s";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    @Override
    public String profileImageKey(UUID accountId) {
        return PROFILE_KEY_FORMAT.formatted(accountId);
    }

    @Override
    public String publicUrl(String key) {
        return PUBLIC_URL_FORMAT.formatted(
                storageProperties.bucket(), storageProperties.region(), key);
    }

    @Override
    public String presignUpload(String key, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(key)
                // 서명에 들어가므로 브라우저는 이 타입으로만 올릴 수 있음
                // S3 가 이 값을 객체에 저장해 두었다가 조회할 때 그대로 돌려줌
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(storageProperties.uploadExpiresSeconds()))
                .putObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    @Override
    public String presignDownload(String key) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(storageProperties.downloadExpiresSeconds()))
                .getObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(key)
                .build());

        log.info("객체를 지웠습니다: key={}", key);
    }
}
