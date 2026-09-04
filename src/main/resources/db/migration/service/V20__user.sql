-- 이 서비스의 첫 마이그레이션 스크립트입니다.
-- V1 부터 V19 는 공통 모듈이 사용하는 대역이므로 쓰지 않습니다.
--
-- 이미 적용된 스크립트는 수정하지 않습니다.
-- 내용이 바뀌면 체크섬이 달라져 다음 기동이 실패합니다.
-- 변경이 필요하면 다음 번호로 새 스크립트를 만듭니다.
--
-- user_db 에는 이 다섯 테이블과 공통 대역의 outbox · processed_event 가 있습니다.
-- 그 둘은 공통 모듈 jar 의 V1__outbox.sql · V2__inbox.sql 이 만들므로
-- 여기서 다시 만들면 "이미 있는 테이블" 로 기동이 실패합니다.
-- user 는 이벤트를 받기만 하므로 outbox 는 비어 있는 채로 남습니다.
--
-- 날짜와 시각 컬럼을 전부 timestamp 로 통일해 date 와 time 을 쓰지 않습니다.
-- 국내 전용 서비스이므로 시간대 없는 timestamp 를 쓰고 엔티티는 LocalDateTime 으로 받습니다.
-- 모든 컨테이너에 TZ=Asia/Seoul 이 설정돼 있어야 합니다.


-- =============================================================================
-- user_profile
-- =============================================================================
-- 프로필. account.created 이벤트를 받아 만들어집니다.
--
-- 다른 표와 달리 대리 키 id 가 없습니다.
-- 들어오는 열쇠가 항상 account_id 하나이고 계정과 1:1 이라
-- 별도 식별자를 두면 아무도 읽지 않는 컬럼이 됩니다.
--
-- 탈퇴하면 행을 지우지 않고 deleted_at 을 찍은 뒤
-- nickname 을 "탈퇴한 사용자" 로 치환하고 profile_image_url 을 NULL 로 만듭니다.
-- auth 가 email 을 치환하고 provider_user_id 를 NULL 로 만드는 것과 같은 모양입니다.
-- user_db 에서 소프트 딜리트를 하는 표는 여기 하나뿐입니다.

CREATE TABLE user_profile
(
    -- PK 이자 auth 가 만든 값입니다.
    -- 다른 22개 표와 달리 애플리케이션이 만들지 않고 이벤트가 준 값을 그대로 씁니다.
    -- ★엔티티에 @UuidGenerator 를 붙이면 안 됩니다.
    --   붙이면 payload 의 accountId 를 무시하고 새 UUID 를 만들어
    --   오류 없이 auth 와 연결이 끊깁니다.
    account_id        uuid         PRIMARY KEY,

    -- 소셜 가입은 닉네임 없이 오므로 NULL 을 허용합니다.
    -- NULL 자체가 "아직 설정 안 함" 의 판별입니다.
    -- 폭이 20 인 것은 auth 의 회원가입 검증(@Size(min = 2, max = 20))과 맞춘 값입니다.
    nickname          varchar(20),

    -- S3 주소입니다. 키 설계에 따라 길이가 달라지므로 폭을 못 박지 않습니다.
    profile_image_url text,

    -- 검색과 판정의 기본 기준이 되는 반려동물입니다.
    -- pet_db 의 값이라 외래 키를 걸지 않습니다.
    -- 펫이 0마리이거나 대표로 지정한 아이를 지우면 NULL 입니다.
    default_pet_id    uuid,

    -- 아래 6개 컬럼은 공통 모듈의 BaseEntity 와 짝을 이룹니다.
    -- 빠뜨리면 ddl-auto: validate 가 기동을 막습니다.
    created_at        timestamp    NOT NULL,
    created_by        varchar(45)  NOT NULL,
    updated_at        timestamp    NOT NULL,
    updated_by        varchar(45)  NOT NULL,
    deleted_at        timestamp,
    deleted_by        varchar(45)
);

COMMENT ON TABLE user_profile IS '프로필. account.created 로 만들어지고 탈퇴 시 익명화됩니다.';


-- =============================================================================
-- favorite
-- =============================================================================
-- 즐겨찾기. notification 이 조건 변경 대상자를 찾을 때 이 표를 읽습니다.
--
-- 하드 딜리트입니다. 해제하면 행을 지웁니다.
-- 소프트 딜리트로 두면 아래 UNIQUE 가 deleted_at 을 보지 않으므로
-- 껐다가 다시 켤 때 INSERT 가 충돌합니다.
-- 하트는 껐다 켰다 하는 기능이라 탈퇴와 달리 흔하게 일어납니다.
-- 남겨서 얻는 것도 없습니다. place_id 목록일 뿐 개인 식별 정보가 아닙니다.

CREATE TABLE favorite
(
    id         uuid         PRIMARY KEY,

    -- 소유자입니다. 게이트웨이가 넣은 X-User-Id 와 대조합니다.
    account_id uuid         NOT NULL,

    -- place_db 의 값이라 외래 키를 걸지 않습니다.
    place_id   uuid         NOT NULL,

    -- 명세 필드 표에는 있으나 화면에 입력 자리가 아직 없습니다.
    -- 선택 필드라 당분간 항상 NULL 이며, 화면이 생기면 서버 변경이 없습니다.
    memo       varchar(200),

    created_at timestamp    NOT NULL,
    created_by varchar(45)  NOT NULL,
    updated_at timestamp    NOT NULL,
    updated_by varchar(45)  NOT NULL,
    deleted_at timestamp,
    deleted_by varchar(45)
);

-- 같은 장소를 두 번 담는 것을 DB 가 막습니다.
-- DELETE /favorites/{placeId} 가 id 가 아니라 placeId 로 지우는 것도
-- 이 쌍이 정확히 한 행이기 때문입니다.
CREATE UNIQUE INDEX uq_favorite_account_place
    ON favorite (account_id, place_id);

COMMENT ON TABLE favorite IS '즐겨찾기. notification 이 조건 변경 대상자를 찾을 때 읽습니다.';


-- =============================================================================
-- visit_log
-- =============================================================================
-- 방문 기록. 지난 날짜 일정 카드의 [다녀왔어요] 를 누르면 만들어집니다.
--
-- 날짜가 지났다고 자동으로 만들지 않습니다.
-- 담아두고 안 간 곳이 방문으로 기록되면
-- stats.visitCount 가 "가려고 계획한 곳 수" 가 되기 때문입니다.
-- itinerary_stop 은 "담은 것", visit_log 는 "갔다고 사용자가 확인한 것" 입니다.
--
-- 하드 딜리트입니다. favorite 과 같은 이유이고,
-- DELETE /itineraries/{stopId} 가 연결된 이 표의 행도 함께 지우므로
-- 소프트 딜리트로 두면 없는 stopId 를 가리키는 행이 남습니다.

CREATE TABLE visit_log
(
    id                uuid         PRIMARY KEY,
    account_id        uuid         NOT NULL,
    place_id          uuid         NOT NULL,

    -- 함께 간 반려동물입니다. 펫이 0마리면 NULL 입니다.
    pet_id            uuid,

    -- 다녀온 일시입니다.
    -- 일정에서 온 방문이면 그 itinerary_stop.visit_at 을 그대로 복사합니다.
    -- 서버 시각을 찍으면 며칠 뒤에 눌렀을 때 값이 틀어집니다.
    visited_at        timestamp    NOT NULL,

    -- 방문 시점의 판정 스냅샷입니다. 조건이 바뀌어도 이 값은 안 바뀝니다.
    -- 즐겨찾기의 판정이 "지금" 인 것과 일부러 다르게 둔 값입니다.
    -- 펫이 0마리면 UNKNOWN 이 들어갑니다.
    -- verdict 호출이 실패하면 UNKNOWN 을 넣지 않고 요청 자체를 실패시킵니다.
    -- 나중에 고칠 방법이 없는 값이라 틀린 값을 남기지 않습니다.
    verdict_at_visit  varchar(16)  NOT NULL,

    -- 어느 일정에서 온 방문인지입니다. 즉흥 방문이면 NULL 입니다.
    -- 아래 UNIQUE 라 같은 카드에서 두 번 눌러도 한 번만 만들어지고,
    -- PostgreSQL 은 NULL 을 서로 다른 값으로 보므로 즉흥 방문끼리는 안 걸립니다.
    itinerary_stop_id uuid,

    memo              varchar(200),

    created_at        timestamp    NOT NULL,
    created_by        varchar(45)  NOT NULL,
    updated_at        timestamp    NOT NULL,
    updated_by        varchar(45)  NOT NULL,
    deleted_at        timestamp,
    deleted_by        varchar(45)
);

CREATE UNIQUE INDEX uq_visit_log_itinerary_stop
    ON visit_log (itinerary_stop_id);

-- GET /api/v1/visits 가 최신순으로 읽습니다.
CREATE INDEX idx_visit_log_account
    ON visit_log (account_id, visited_at DESC);

COMMENT ON TABLE visit_log IS '방문 기록. 일정과 별개로 즉흥 방문도 담습니다.';


-- =============================================================================
-- itinerary_stop
-- =============================================================================
-- 날짜 단위 일정입니다.
-- "제목이 있는 여행" 을 묶는 itinerary 표는 두지 않습니다.
-- title 을 입력받을 자리가 어느 화면에도 없었기 때문입니다.
--
-- 하드 딜리트입니다. 지우면 연결된 visit_log 도 함께 지웁니다.

CREATE TABLE itinerary_stop
(
    id          uuid         PRIMARY KEY,
    account_id  uuid         NOT NULL,
    place_id    uuid         NOT NULL,

    -- 방문 예정 일시입니다.
    -- 시각을 안 정했으면 그 날짜의 00:00 이 들어갑니다.
    -- 날짜로 고르는 조회는 BETWEEN 으로 합니다.
    visit_at    timestamp    NOT NULL,

    -- 동반 예정 동물입니다. 펫이 0마리면 NULL 입니다.
    pet_id      uuid,

    -- 그날 안에서의 순서입니다. 서버가 "그날 마지막 + 1" 로 채웁니다.
    -- 사용자가 직접 바꾸는 기능은 두지 않습니다.
    -- 목록은 visit_at 순으로 정렬하고, 이 값은 시각이 같을 때의 순서입니다.
    -- 시각을 안 정하면 visit_at 이 그날 00:00 이라 여럿이 같은 값이 되기 때문입니다.
    visit_order integer      NOT NULL,

    memo        varchar(200),

    created_at  timestamp    NOT NULL,
    created_by  varchar(45)  NOT NULL,
    updated_at  timestamp    NOT NULL,
    updated_by  varchar(45)  NOT NULL,
    deleted_at  timestamp,
    deleted_by  varchar(45)
);

-- 세 열의 순서가 조회 형태 그대로입니다.
-- WHERE account_id = ? AND visit_at BETWEEN ? AND ? ORDER BY visit_at, visit_order
CREATE INDEX idx_itinerary_stop_account
    ON itinerary_stop (account_id, visit_at, visit_order);

COMMENT ON TABLE itinerary_stop IS '날짜 단위 일정. 담은 장소 하나가 한 행입니다.';


-- =============================================================================
-- daily_summary
-- =============================================================================
-- 마이페이지 동반 기록의 날짜 카드에 붙는 하루 단위 AI 요약문입니다.
-- 사용자가 [AI 요약하기] 를 눌러야 생성됩니다. 자동 생성이나 배치를 두지 않습니다.
--
-- 남용 방지로 같은 날짜 재생성에 쿨다운 1분, 계정당 하루 20건 상한을 둡니다.
-- 그 값들은 Redis 에 두므로 이 표에는 없습니다.
--
-- 대리 키 없이 복합 PK 인 것이 곧 "하루에 한 줄" 을 강제하는 장치입니다.
-- upsert 만 하면 중복이 원천적으로 생기지 않습니다.

CREATE TABLE daily_summary
(
    account_id   uuid        NOT NULL,

    -- 요약 대상 날짜입니다. 서버가 반드시 00:00 으로 고정해 넣습니다.
    -- 시각이 섞이면 PK 가 "하루에 한 줄" 을 못 막습니다.
    -- API 는 visitDate 를 날짜 문자열로 받으므로 서버가 00:00 을 붙이는 지점이
    -- 한 곳으로 모여 있어야 합니다.
    visit_date   timestamp   NOT NULL,

    -- LLM 이 만든 요약문입니다. 길이를 예측할 수 없어 text 입니다.
    summary      text        NOT NULL,

    -- 생성 시각입니다. [갱신하기] 를 언제 눌렀는지가 이 값입니다.
    generated_at timestamp   NOT NULL,

    created_at   timestamp   NOT NULL,
    created_by   varchar(45) NOT NULL,
    updated_at   timestamp   NOT NULL,
    updated_by   varchar(45) NOT NULL,
    deleted_at   timestamp,
    deleted_by   varchar(45),

    PRIMARY KEY (account_id, visit_date)
);

COMMENT ON TABLE daily_summary IS '하루 단위 AI 요약. 사용자가 버튼을 눌러야 생성됩니다.';
