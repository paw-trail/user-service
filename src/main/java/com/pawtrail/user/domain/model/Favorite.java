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
import org.hibernate.annotations.UuidGenerator;

/**
 * 즐겨찾기입니다.
 *
 * notification 이 조건 변경 대상자를 찾을 때 이 표를 읽습니다.
 *
 * 하드 딜리트입니다.
 * 소프트 딜리트로 두면 uq_favorite_account_place 가 deleted_at 을 보지 않아
 * 해제한 뒤 다시 담을 때 INSERT 가 충돌합니다.
 * 하트는 껐다 켰다 하는 기능이라 탈퇴와 달리 흔하게 일어납니다.
 *
 * BaseEntity 는 그대로 상속합니다.
 * created_at 이 GET /favorites 응답의 createdAt 으로 나가기 때문입니다.
 * deleted_at 과 deleted_by 는 항상 null 인 채로 남습니다.
 */
@Entity
@Table(name = "favorite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // 소유자임
    // 게이트웨이가 넣은 X-User-Id 와 대조하는 값임
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    // place_db 의 값이라 외래 키를 걸지 않음
    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    // 명세 필드 표에는 있으나 화면에 입력 자리가 아직 없음
    // 선택 필드라 당분간 항상 null 이며, 화면이 생기면 서버 변경이 없음
    @Column(name = "memo", length = 200)
    private String memo;

    private Favorite(UUID accountId, UUID placeId, String memo) {
        this.accountId = accountId;
        this.placeId = placeId;
        this.memo = memo;
    }

    /**
     * 즐겨찾기를 담습니다.
     *
     * 같은 장소를 두 번 담는 것은 uq_favorite_account_place 가 막습니다.
     * 애플리케이션에서 먼저 조회해 거르지 않는 이유는
     * 조회와 저장 사이에 다른 요청이 끼어들 수 있기 때문입니다.
     */
    public static Favorite create(UUID accountId, UUID placeId, String memo) {
        if (accountId == null || placeId == null) {
            throw new IllegalArgumentException("accountId 와 placeId 는 필수입니다.");
        }
        return new Favorite(accountId, placeId, memo);
    }
}
