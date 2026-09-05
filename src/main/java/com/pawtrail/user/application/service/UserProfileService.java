package com.pawtrail.user.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.application.dto.input.ProfileUpdateInput;
import com.pawtrail.user.application.dto.output.ProfileOutput;
import com.pawtrail.user.application.dto.output.UserSummaryOutput;
import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import com.pawtrail.user.domain.repository.VisitLogRepository;
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

        ProfileOutput.Stats stats = new ProfileOutput.Stats(
                visitLogRepository.countByAccountId(accountId),
                // review 서비스를 세우면 GET /internal/reviews/count?accountId= 로 채움
                null,
                favoriteRepository.countByAccountId(accountId));

        return ProfileOutput.of(profile, stats);
    }

    /**
     * 닉네임과 사진 주소를 바꿉니다.
     *
     * 둘 다 null 을 그대로 반영합니다. 지운다는 뜻입니다.
     *
     * 바꾼 뒤 save 를 부르지 않습니다.
     * 트랜잭션 안에서 조회한 엔티티라 변경 감지가 커밋 시점에 UPDATE 를 냅니다.
     * 여기서 save 를 부르면 같은 일을 두 번 시키는 셈입니다.
     */
    @Transactional
    public ProfileOutput updateProfile(UUID accountId, ProfileUpdateInput input) {
        UserProfile profile = getOrThrow(accountId);

        profile.updateProfile(input.nickname(), input.profileImageUrl());

        ProfileOutput.Stats stats = new ProfileOutput.Stats(
                visitLogRepository.countByAccountId(accountId),
                null,
                favoriteRepository.countByAccountId(accountId));

        log.info("프로필을 수정했습니다: accountId={}", accountId);
        return ProfileOutput.of(profile, stats);
    }

    /**
     * 대표 반려동물을 바꿉니다.
     *
     * petId 가 null 이면 해제입니다. 유효한 요청입니다.
     * 반려동물이 0마리인 상태를 정식으로 지원하기 때문입니다.
     *
     * 그 petId 가 실제로 있는지, 이 사람 것인지는 확인하지 않습니다.
     * pet 서비스를 호출해야 알 수 있는데 아직 그 기반이 없습니다.
     * 검증은 외부 호출 기반을 세우는 이슈에서 함께 붙입니다.
     */
    @Transactional
    public void changeDefaultPet(UUID accountId, UUID petId) {
        UserProfile profile = getOrThrow(accountId);

        profile.changeDefaultPet(petId);

        log.info("대표 반려동물을 바꿨습니다: accountId={}, petId={}", accountId, petId);
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
                .map(UserSummaryOutput::from)
                .toList();
    }

    private UserProfile getOrThrow(UUID accountId) {
        return userProfileRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }
}
