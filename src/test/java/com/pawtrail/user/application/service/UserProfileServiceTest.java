package com.pawtrail.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.application.dto.output.ProfileOutput;
import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.provider.ReviewProvider;
import com.pawtrail.user.domain.provider.StorageProvider;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 프로필 조회의 자가 복구와 가입 이벤트 소비의 세 갈래를 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 띄우지 않습니다.
 * 만드는 일이 실제로 새 트랜잭션으로 떨어져 나가는지는 여기서 볼 수 없어 컨테이너 확인에서 봅니다.
 * 여기서 보는 것은 행의 상태에 따라 무엇을 만들고, 무엇을 돌려주고, 무엇을 덮지 않는가입니다.
 *
 * 만드는 쪽(UserProfileRecoveryService)은 목으로 둡니다.
 * 기본 키 충돌은 그 목이 DataIntegrityViolationException 을 던지는 것으로 흉내 냅니다.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserProfileRecoveryService userProfileRecoveryService;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private VisitLogRepository visitLogRepository;

    @Mock
    private ReviewProvider reviewProvider;

    @Mock
    private StorageProvider storageProvider;

    @InjectMocks
    private UserProfileService userProfileService;

    private static final UUID ACCOUNT_ID = UUID.fromString("01a0726a-64e9-7712-9ecb-4996e2dcd75f");
    private static final String SIGNUP_NICKNAME = "다정이네";

    // 이벤트 소비 경로의 감사 주체 이름임
    private static final String SYSTEM = "SYSTEM";

    // ── GET /users/me — 자가 복구 ──────────────────────────────

    @Test
    @DisplayName("프로필이 있으면 그대로 돌려주고 만들지 않는다")
    void 있으면_그대로() {
        givenProfile(UserProfile.create(ACCOUNT_ID, SIGNUP_NICKNAME));

        ProfileOutput output = userProfileService.getMyProfile(ACCOUNT_ID);

        assertThat(output.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(output.nickname()).isEqualTo(SIGNUP_NICKNAME);
        verify(userProfileRecoveryService, never()).createEmpty(any());
    }

    @Test
    @DisplayName("행이 아예 없으면 닉네임 없이 만들어 돌려준다")
    void 없으면_만든다() {
        givenNoProfile();
        when(userProfileRecoveryService.createEmpty(ACCOUNT_ID))
                .thenReturn(UserProfile.create(ACCOUNT_ID, null));

        ProfileOutput output = userProfileService.getMyProfile(ACCOUNT_ID);

        assertThat(output.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(output.nickname()).isNull();
        verify(userProfileRecoveryService, times(1)).createEmpty(ACCOUNT_ID);
    }

    @Test
    @DisplayName("삭제 표시가 있으면 만들지 않고 404 다")
    void 삭제_표시면_404() {
        givenProfile(UserProfile.withdrawnMarker(ACCOUNT_ID, SYSTEM));

        // 탈퇴한 계정의 토큰이 남아 들어온 요청이라도 프로필을 되살리면 안 됨
        assertThatThrownBy(() -> userProfileService.getMyProfile(ACCOUNT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
        verify(userProfileRecoveryService, never()).createEmpty(any());
    }

    @Test
    @DisplayName("만드는 사이 먼저 생긴 행이 있으면 그 행을 다시 읽어 돌려준다")
    void 충돌하면_다시_읽는다() {
        // 처음 읽을 때는 없었고, 만드는 사이 가입 이벤트 소비가 닉네임을 넣어 만들었음
        // thenReturn 을 이어 붙이는 이유 — 두 값을 한 번에 넘기면 제네릭 가변 인자라 컴파일 경고가 남
        when(userProfileRepository.findByIdIncludingDeleted(ACCOUNT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(UserProfile.create(ACCOUNT_ID, SIGNUP_NICKNAME)));
        when(userProfileRecoveryService.createEmpty(ACCOUNT_ID))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ProfileOutput output = userProfileService.getMyProfile(ACCOUNT_ID);

        // 먼저 생긴 행의 닉네임이 그대로 나감 — 덮어쓰지 않음
        assertThat(output.nickname()).isEqualTo(SIGNUP_NICKNAME);
        verify(userProfileRepository, times(2)).findByIdIncludingDeleted(ACCOUNT_ID);
    }

    @Test
    @DisplayName("충돌 뒤 다시 읽은 행이 삭제 표시면 404 다")
    void 충돌_뒤_삭제_표시면_404() {
        when(userProfileRepository.findByIdIncludingDeleted(ACCOUNT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(UserProfile.withdrawnMarker(ACCOUNT_ID, SYSTEM)));
        when(userProfileRecoveryService.createEmpty(ACCOUNT_ID))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> userProfileService.getMyProfile(ACCOUNT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    // ── account.created — 세 갈래 ──────────────────────────────

    @Test
    @DisplayName("행이 없으면 가입 닉네임으로 만들고, merge 하는 save 가 아니라 create 로 넣는다")
    void 없으면_가입_닉네임으로_만든다() {
        givenNoProfile();

        userProfileService.createFromAccountCreated(ACCOUNT_ID, SIGNUP_NICKNAME);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).create(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(captor.getValue().getNickname()).isEqualTo(SIGNUP_NICKNAME);
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("삭제 표시면 만들지도 채우지도 않는다")
    void 삭제_표시면_건너뛴다() {
        UserProfile marker = UserProfile.withdrawnMarker(ACCOUNT_ID, SYSTEM);
        givenProfile(marker);

        userProfileService.createFromAccountCreated(ACCOUNT_ID, SIGNUP_NICKNAME);

        // 탈퇴로 신원을 지운 자리에 가입 닉네임을 되살리면 안 됨
        assertThat(marker.getNickname()).isNull();
        verify(userProfileRepository, never()).create(any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("자가 복구로 먼저 만들어진 프로필의 비어 있는 닉네임을 채운다")
    void 비어_있는_닉네임을_채운다() {
        UserProfile recovered = UserProfile.create(ACCOUNT_ID, null);
        givenProfile(recovered);

        userProfileService.createFromAccountCreated(ACCOUNT_ID, SIGNUP_NICKNAME);

        assertThat(recovered.getNickname()).isEqualTo(SIGNUP_NICKNAME);
        // 조회해 온 엔티티라 변경 감지가 커밋 시점에 UPDATE 를 냄
        verify(userProfileRepository, never()).create(any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 닉네임이 있으면 덮지 않는다")
    void 닉네임이_있으면_덮지_않는다() {
        // 자가 복구 뒤 이벤트가 오기 전에 사용자가 닉네임을 직접 정한 경우
        UserProfile profile = UserProfile.create(ACCOUNT_ID, "바꾼이름");
        givenProfile(profile);

        userProfileService.createFromAccountCreated(ACCOUNT_ID, SIGNUP_NICKNAME);

        assertThat(profile.getNickname()).isEqualTo("바꾼이름");
        verify(userProfileRepository, never()).create(any());
    }

    @Test
    @DisplayName("소셜 가입이라 닉네임 없이 오면 채울 것이 없다")
    void 소셜_가입은_채울_것이_없다() {
        UserProfile recovered = UserProfile.create(ACCOUNT_ID, null);
        givenProfile(recovered);

        userProfileService.createFromAccountCreated(ACCOUNT_ID, null);

        assertThat(recovered.getNickname()).isNull();
        verify(userProfileRepository, never()).create(any());
    }

    // ── 도우미 ──────────────────────────────────────────────────

    private void givenProfile(UserProfile profile) {
        when(userProfileRepository.findByIdIncludingDeleted(ACCOUNT_ID)).thenReturn(Optional.of(profile));
    }

    private void givenNoProfile() {
        when(userProfileRepository.findByIdIncludingDeleted(ACCOUNT_ID)).thenReturn(Optional.empty());
    }
}
