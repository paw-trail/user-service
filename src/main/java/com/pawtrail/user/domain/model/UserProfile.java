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

    // 탈퇴할 때 닉네임 자리에 넣는 값임
    //
    // 화면에 보이라고 두는 값이 아님
    // 탈퇴한 프로필은 @SQLRestriction 때문에 어느 조회에도 안 걸림
    // auth 가 이메일과 제공자 식별자를 끊는 것과 짝을 맞춰 신원을 지우는 것이 목적임
    //
    // null 로 비우지 않는 이유
    // 이 컬럼의 null 은 이미 "아직 설정 안 함" 이라는 뜻을 가지고 있어
    // 비우면 소셜 가입 직후와 탈퇴 후가 같은 모양이 됨
    private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

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
     * 닉네임을 바꿉니다.
     *
     * 사진과 따로 둔 것은 부르는 조건이 다르기 때문입니다.
     * 닉네임은 값이 왔을 때만 바꾸고, 사진은 필드가 요청에 있었으면 null 이어도 반영합니다.
     * 한 메서드로 묶으면 "이번에는 어느 쪽을 건드리는지" 를 인자로 또 전해야 합니다.
     *
     * null 을 받지 않습니다. 닉네임을 지우는 동작이 없기 때문입니다.
     * 요청 계층이 이미 막지만, 다른 경로로 들어와도 여기서 걸립니다.
     *
     * 길이 검증은 요청 계층이 합니다.
     * 여기서 또 보면 같은 규칙이 두 곳에 생겨 한쪽만 고쳐질 자리가 됩니다.
     */
    public void changeNickname(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("닉네임은 지울 수 없습니다.");
        }
        this.nickname = nickname;
    }

    /**
     * 프로필 사진 주소를 바꿉니다.
     *
     * null 을 그대로 반영합니다. 지운다는 뜻입니다.
     * 사진이 없으면 화면에 기본 이미지가 뜨므로 성립하는 상태입니다.
     */
    public void changeProfileImageUrl(String profileImageUrl) {
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

    /**
     * 탈퇴한 계정의 신원을 지우고 삭제 표시를 남깁니다.
     *
     * 세 가지를 함께 합니다. 닉네임을 치환하고, 사진 주소를 비우고, 삭제 시각을 찍습니다.
     * 하나로 묶은 것은 셋이 항상 같이 일어나기 때문입니다.
     * 나눠 두면 부르는 쪽이 세 줄을 순서대로 불러야 하고, 하나를 빠뜨려도 오류가 나지 않습니다.
     *
     * changeNickname 과 changeProfileImageUrl 을 쓰지 않습니다.
     * 그 둘은 부르는 조건이 서로 달라 나눠 둔 것인데,
     * 여기는 조건이 아니라 둘을 항상 함께 바꾸는 동작입니다.
     *
     * 사진 파일 자체는 여기서 지우지 않습니다.
     * 엔티티가 객체 저장소를 알면 계층이 무너지고, 그 삭제는 되돌릴 수 없어
     * 트랜잭션이 커밋된 뒤에 따로 해야 합니다.
     *
     * 이미 삭제 표시가 있으면 시각이 덮이지 않습니다. BaseEntity 가 막습니다.
     *
     * @param deletedBy 삭제자입니다. 이벤트 소비 경로라 실제로는 시스템 이름이 들어옵니다.
     */
    public void anonymize(String deletedBy) {
        this.nickname = WITHDRAWN_NICKNAME;
        this.profileImageUrl = null;
        delete(deletedBy);
    }

    /**
     * 프로필이 없는 계정에 대해 삭제 표시만 남긴 행을 만듭니다.
     *
     * account.created 가 발행에 실패해 멈춰 있는 사이 사용자가 탈퇴하면
     * account.withdrawn 이 먼저 도착해 지울 프로필이 없습니다.
     * 그때 아무것도 하지 않으면 나중에 재발행된 account.created 가 도착해
     * 이미 탈퇴한 계정의 프로필이 뒤늦게 생깁니다.
     *
     * 그래서 계정 식별자만 채우고 삭제 시각을 찍은 행을 미리 만들어 둡니다.
     * account.created 를 소비할 때 existsIncludingDeleted 가 이 행을 보고 멈춥니다.
     *
     * 닉네임을 치환하지 않고 비워 둡니다.
     * 치환의 목적이 auth 가 끊은 신원이 이쪽에 남지 않게 하는 것인데,
     * 이 행은 애초에 닉네임을 가진 적이 없어 지울 신원이 없습니다.
     *
     * 만들면서 곧바로 삭제 표시를 찍습니다.
     * 두 단계로 나누면 하나만 부른 순간 살아 있는 빈 프로필이 생기고,
     * 그러면 탈퇴한 사람이 서비스를 계속 쓸 수 있게 됩니다.
     *
     * @param deletedBy 삭제자입니다. anonymize 와 같은 값이 들어옵니다.
     */
    public static UserProfile withdrawnMarker(UUID accountId, String deletedBy) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId 는 필수입니다.");
        }

        UserProfile marker = new UserProfile(accountId, null);
        marker.delete(deletedBy);
        return marker;
    }
}
