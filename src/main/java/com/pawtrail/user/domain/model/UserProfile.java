package com.pawtrail.user.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 프로필입니다.
 *
 * account.created 이벤트를 받아 만들어집니다.
 * 사용자가 직접 만드는 경로는 없습니다.
 *
 * user_db 에서 소프트 딜리트를 하는 표는 이것 하나뿐입니다.
 * 나머지 넷은 하드 딜리트라 @SQLRestriction 도 여기에만 붙습니다.
 * 소프트 딜리트를 고른 이유가 "신원을 끊되 추적 근거는 남긴다" 인데
 * 신원이 담긴 표가 이것뿐이기 때문입니다.
 */
@Entity
@Table(name = "user_profile")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseEntity {

    // 기본 키이자 auth 가 만든 값임
    //
    // 다른 스물두 개 표와 달리 애플리케이션이 만들지 않음
    // account.created payload 의 accountId 를 그대로 받아 넣음
    //
    // @UuidGenerator 를 붙이면 안 됨
    // 붙이면 payload 의 값을 무시하고 새 UUID 를 만들어
    // 오류 없이 auth 와 연결이 끊김, X-User-Id 로 조회하면 영원히 못 찾음
    //
    // 대리 키 id 를 따로 두지 않는 이유
    // 들어오는 열쇠가 항상 accountId 하나이고 계정과 1:1 이라
    // 별도 식별자를 두면 아무도 읽지 않는 컬럼이 됨
    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    // 후기 작성자와 제보자 이름으로 쓰임
    //
    // 소셜 가입은 닉네임 없이 오므로 null 을 허용함
    // null 자체가 "아직 설정 안 함" 의 판별임
    //
    // 폭 20 은 auth 의 회원가입 검증(@Size(min = 2, max = 20))과 맞춘 값임
    // 세 자리가 갈려 있던 것을 auth 쪽에 맞춤, 이미 도는 값이라 바꾸는 비용이 없었음
    @Column(name = "nickname", length = 20)
    private String nickname;

    // S3 주소임
    // 키 설계에 따라 길이가 달라지므로 폭을 못 박지 않고 text 로 둠
    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    // 검색과 판정의 기본 기준이 되는 반려동물임
    //
    // pet_db 의 값이라 외래 키를 걸지 않음
    // 펫이 0마리이거나 대표로 지정한 아이를 지우면 null 임
    @Column(name = "default_pet_id")
    private UUID defaultPetId;

    private UserProfile(UUID accountId, String nickname) {
        this.accountId = accountId;
        this.nickname = nickname;
    }

    /**
     * account.created 를 받아 프로필을 만듭니다.
     *
     * 닉네임은 소셜 가입이면 비어서 옵니다.
     * 사진과 대표 반려동물은 이 시점에 있을 수가 없어 받지 않습니다.
     */
    public static UserProfile create(UUID accountId, String nickname) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId 는 필수입니다.");
        }
        return new UserProfile(accountId, nickname);
    }

    /**
     * 닉네임과 사진 주소를 바꿉니다.
     *
     * null 을 그대로 반영합니다. 지운다는 뜻입니다.
     * "안 보냈다" 와 "null 을 보냈다" 를 가르지 않는 것은
     * 화면이 두 값을 항상 함께 보내기 때문입니다.
     *
     * 길이 검증은 요청 계층이 이미 했습니다.
     * 여기서 또 보면 같은 규칙이 두 곳에 생겨 한쪽만 고쳐질 자리가 됩니다.
     */
    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 대표 반려동물을 바꿉니다.
     *
     * null 을 받으면 해제입니다. 잘못된 요청이 아니라 유효한 요청입니다.
     * 반려동물이 0마리인 상태를 정식으로 지원하기 때문입니다.
     *
     * 그 petId 가 존재하는지, 이 사람 것인지는 여기서 보지 않습니다.
     * pet 서비스를 호출해야 알 수 있고 아직 그 기반이 없습니다.
     */
    public void changeDefaultPet(UUID petId) {
        this.defaultPetId = petId;
    }
}
