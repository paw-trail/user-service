package com.pawtrail.user.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.application.dto.input.ProfileUpdateInput;
import com.pawtrail.user.application.dto.output.ProfileOutput;
import com.pawtrail.user.application.dto.output.UploadUrlOutput;
import com.pawtrail.user.application.dto.output.UserSummaryOutput;
import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.provider.StorageProvider;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import com.pawtrail.user.infrastructure.config.StorageProperties;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필을 다루는 서비스입니다.
 *
 * 이벤트로 만드는 것과 API 로 읽고 고치는 것을 한 클래스에 모읍니다.
 * auth 도 AccountService 하나에 계정 관련 동작을 모았습니다.
 *
 * @Transactional 을 클래스 단위로 붙이지 않습니다.
 * 이벤트 경로는 InboxProcessor.processOnce 가 이미 트랜잭션을 열고 있어
 * 여기에 또 붙이면 경계가 어디인지 읽는 사람이 매번 따져야 합니다.
 * API 경로에서 부르는 메서드에만 붙입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final FavoriteRepository favoriteRepository;
    private final VisitLogRepository visitLogRepository;
    private final StorageProvider storageProvider;
    private final StorageProperties storageProperties;

    /**
     * account.created 를 받아 프로필을 만듭니다.
     *
     * 이미 있으면 아무 것도 하지 않고 넘어갑니다.
     * 두 가지 경우가 여기에 걸립니다.
     *
     *   같은 이벤트가 두 번 처리되려는 경우
     *     processed_event 가 먼저 걸러 주므로 사실상 오지 않지만,
     *     기본 키 충돌로 실패하는 것보다 조용히 넘어가는 편이 낫습니다.
     *
     *   탈퇴가 먼저 처리된 경우
     *     account.created 가 발행에 실패해 멈춰 있는 사이 사용자가 탈퇴하면
     *     account.withdrawn 이 먼저 도착합니다.
     *     그때 탈퇴 소비자가 account_id 만 채우고 deleted_at 을 찍은 행을 만들어 두므로,
     *     나중에 재발행된 account.created 가 도착해도 여기서 멈춥니다.
     *     막지 않으면 탈퇴한 계정의 프로필이 뒤늦게 생겨 아무 API 에도 안 잡히는
     *     고아 행으로 남습니다.
     *
     * 존재 확인에 findById 를 쓰지 않는 이유는 UserProfile 에 걸린
     * @SQLRestriction("deleted_at IS NULL") 이 삭제 표시 행을 가리기 때문입니다.
     * 그 제한을 우회하는 existsIncludingDeleted 를 씁니다.
     */
    public void createFromAccountCreated(UUID accountId, String nickname) {
        if (userProfileRepository.existsIncludingDeleted(accountId)) {
            log.info("이미 처리된 계정입니다. 프로필을 만들지 않습니다: accountId={}", accountId);
            return;
        }

        userProfileRepository.save(UserProfile.create(accountId, nickname));

        log.info("프로필을 만들었습니다: accountId={}", accountId);
    }

    /**
     * 마이페이지가 보는 프로필을 조립합니다.
     *
     * stats 셋 중 둘은 우리 표를 세고 reviewCount 만 review 서비스가 줍니다.
     * 그 서비스가 아직 없어 지금은 null 을 넣습니다.
     * 명세도 "호출 실패 시 null" 로 정해 두었으므로 프론트가 이 상태를 다룹니다.
     * 통계 하나 때문에 마이페이지가 안 뜨면 안 됩니다.
     *
     * 가입 직후에는 404 가 날 수 있습니다.
     * 회원가입이 자동 로그인이라 account.created 를 처리하기 전에 이 요청이 닿을 수 있습니다.
     * 실제 창은 밀리초라 프론트가 짧게 재시도하면 지나갑니다.
     */
    @Transactional(readOnly = true)
    public ProfileOutput getMyProfile(UUID accountId) {
        UserProfile profile = getOrThrow(accountId);

        return toOutput(profile);
    }

    /**
     * 프로필 사진을 올릴 주소를 발급합니다.
     *
     * 이 서버는 파일을 받지 않습니다. 주소만 만들어 주고 브라우저가 S3 로 직접 PUT 합니다.
     * 그래서 서버가 파일 크기만큼의 메모리도 대역폭도 쓰지 않습니다.
     *
     * 프로필이 있는지 먼저 확인합니다.
     * 없는 계정에 주소를 내주면 올릴 수는 있는데 그 뒤에 붙일 자리가 없습니다.
     *
     * 크기 상한은 여기서 봅니다.
     * 서명에도 크기가 들어가지만 그것은 "요청한 크기와 다른 것" 을 막을 뿐
     * 상한을 막지는 못합니다. 20MB 를 달라고 하면 20MB 짜리 서명이 나갑니다.
     *
     * 상한을 S3 쪽에 맡길 방법이 없습니다.
     * presigned PUT 에는 범위 조건을 걸 수 없고,
     * 버킷 정책에도 크기를 보는 조건 키가 없습니다.
     * content-length-range 는 presigned POST 의 폼 정책에만 있습니다.
     *
     * 넘으면 주소를 아예 내주지 않으므로 S3 로 요청이 가지도 않습니다.
     *
     * 데이터베이스를 고치지 않으므로 트랜잭션은 읽기 전용입니다.
     * 실제로 사진이 붙는 것은 프론트가 PATCH /users/me 를 부를 때입니다.
     */
    @Transactional(readOnly = true)
    public UploadUrlOutput issueUploadUrl(UUID accountId, String contentType, long contentLength) {
        getOrThrow(accountId);

        if (contentLength > storageProperties.maxImageBytes()) {
            log.warn("이미지가 상한을 넘었습니다: accountId={}, contentLength={}, max={}",
                    accountId, contentLength, storageProperties.maxImageBytes());
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        String key = storageProvider.profileImageKey(accountId);

        return new UploadUrlOutput(
                storageProvider.presignUpload(key, contentType, contentLength),
                storageProvider.publicUrl(key),
                storageProperties.uploadExpiresSeconds());
    }

    /**
     * 닉네임과 사진 주소를 바꿉니다.
     *
     * 요청에 담겨 온 것만 바꿉니다. 안 보낸 필드는 건드리지 않습니다.
     * 사진만 바꾸려고 보낸 요청이 닉네임까지 지우면 안 되기 때문입니다.
     *
     * 두 필드의 조건이 다릅니다.
     *   nickname          값이 왔을 때만 바꿈. null 은 "안 보냈다" 하나만 뜻함
     *                     명시적 null 은 요청 계층이 이미 400 으로 막았음
     *   profileImageUrl   필드가 요청에 있었으면 반영함. null 이면 지운다는 뜻
     *
     * 바꾼 뒤 save 를 부르지 않습니다.
     * 트랜잭션 안에서 조회한 엔티티라 변경 감지가 커밋 시점에 UPDATE 를 냅니다.
     * 여기서 save 를 부르면 같은 일을 두 번 시키는 셈입니다.
     */
    @Transactional
    public ProfileOutput updateProfile(UUID accountId, ProfileUpdateInput input) {
        UserProfile profile = getOrThrow(accountId);

        if (input.nickname() != null) {
            profile.changeNickname(input.nickname());
        }
        if (input.profileImageUrlProvided()) {
            profile.changeProfileImageUrl(toStoredKey(accountId, input.profileImageUrl()));
        }

        log.info("프로필을 수정했습니다: accountId={}", accountId);
        return toOutput(profile);
    }

    /**
     * 요청이 들고 온 사진 주소를 저장할 키로 바꿉니다.
     *
     * 저장하는 것은 주소가 아니라 키입니다.
     * 조회 방식이 바뀌어도 데이터를 안 건드리기 위해서입니다.
     * 나중에 CloudFront 를 앞에 붙이면 조립하는 쪽만 고치면 됩니다.
     *
     * 그 전에 서버가 만든 정답과 대조합니다.
     * 키가 계정당 하나로 고정이라 정답이 하나뿐입니다.
     * 이 대조가 없으면 이런 값들이 그대로 저장됩니다.
     *   프론트가 실수로 보낸 uploadUrl — 서명이 붙어 있어 조회 때 깨짐
     *   남의 사진 주소 — 남의 사진이 내 프로필에 뜸
     *   아무 문자열 — 조회 때 깨짐
     */
    private String toStoredKey(UUID accountId, String url) {
        if (url == null) {
            return null;
        }

        String key = storageProvider.profileImageKey(accountId);
        if (!storageProvider.publicUrl(key).equals(url)) {
            log.warn("프로필 사진 주소가 발급한 것과 다릅니다: accountId={}", accountId);
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
        return key;
    }

    /**
     * 응답으로 내보낼 형태로 만듭니다.
     *
     * 사진은 저장된 키에 서명을 붙여 실제로 열리는 주소로 바꿉니다.
     * 버킷이 퍼블릭 액세스를 차단해 두어 서명 없이는 안 열립니다.
     *
     * stats 셋 중 둘은 우리 표를 세고 reviewCount 만 review 서비스가 줍니다.
     * 그 서비스가 아직 없어 지금은 null 입니다.
     * 명세도 "호출 실패 시 null" 로 정해 두었으므로 프론트가 이 상태를 다룹니다.
     * 통계 하나 때문에 마이페이지가 안 뜨면 안 됩니다.
     */
    private ProfileOutput toOutput(UserProfile profile) {
        UUID accountId = profile.getAccountId();

        ProfileOutput.Stats stats = new ProfileOutput.Stats(
                visitLogRepository.countByAccountId(accountId),
                // review 서비스를 세우면 GET /internal/reviews/count?accountId= 로 채움
                null,
                favoriteRepository.countByAccountId(accountId));

        return new ProfileOutput(
                accountId,
                profile.getNickname(),
                presignOrNull(profile.getProfileImageUrl()),
                profile.getDefaultPetId(),
                stats);
    }

    private String presignOrNull(String key) {
        return key == null ? null : storageProvider.presignDownload(key);
    }

    /**
     * 대표 반려동물을 바꿉니다.
     *
     * 지금은 해제만 됩니다. 값을 보내면 400 입니다.
     *
     * 그 petId 가 실제로 있는지, 이 사람 것인지를 확인할 방법이 아직 없기 때문입니다.
     * pet 서비스를 호출해야 하는데 그 서비스도, 서비스 간 호출 기반도 아직 없습니다.
     *
     * 확인하지 못하는 값을 저장하면 남의 반려동물 식별자가 여기 들어올 수 있습니다.
     * 그러면 검색과 판정이 그 반려동물 기준으로 돌아갑니다.
     * petIds 를 생략한 요청은 서버가 이 값을 쓰기 때문입니다.
     * GET /internal/pets?ids= 에 소유권 검증을 필수로 둔 것과 같은 이유이고,
     * 이 컬럼은 그 검증을 우회하는 경로가 됩니다.
     *
     * 지금 막아도 화면이 막히지 않습니다.
     * pet 서비스가 없어 지정할 반려동물 자체가 존재하지 않습니다.
     *
     * 외부 호출 기반을 세우는 이슈에서 이 블록을 검증 호출로 바꿉니다.
     * 그때까지 열어 두면 검증을 붙이는 것을 잊어도 드러나지 않습니다.
     */
    @Transactional
    public void changeDefaultPet(UUID accountId, UUID petId) {
        if (petId != null) {
            log.warn("대표 반려동물 지정을 아직 지원하지 않습니다: accountId={}, petId={}",
                    accountId, petId);
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        UserProfile profile = getOrThrow(accountId);

        profile.changeDefaultPet(null);

        log.info("대표 반려동물을 해제했습니다: accountId={}", accountId);
    }

    /**
     * 여러 사람의 닉네임과 사진을 한 번에 돌려줍니다.
     *
     * review 가 후기 목록의 작성자를, report 가 제보자를 채울 때 씁니다.
     *
     * 없는 식별자는 결과에서 그냥 빠집니다. 오류로 보지 않습니다.
     * 부르는 쪽이 자기 목록과 맞춰 쓰므로 빠진 것은 이름 없이 그리면 됩니다.
     * 탈퇴한 사람도 @SQLRestriction 때문에 빠지는데,
     * 그 경우에도 부르는 쪽이 "탈퇴한 사용자" 로 표시하면 됩니다.
     *
     * 소유권 검증을 두지 않습니다.
     * 반환하는 닉네임과 사진은 후기 목록에 그대로 나오는 값이고
     * accountId 도 그 목록에 이미 있어 숨길 것이 없습니다.
     * 여러 사람을 한 번에 물어보는 배치 조회라 "내 것" 이라는 개념도 없습니다.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryOutput> getSummaries(Collection<UUID> accountIds) {
        return userProfileRepository.findAllById(accountIds).stream()
                .map(profile -> new UserSummaryOutput(
                        profile.getAccountId(),
                        profile.getNickname(),
                        presignOrNull(profile.getProfileImageUrl())))
                .toList();
    }

    private UserProfile getOrThrow(UUID accountId) {
        return userProfileRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }
}
