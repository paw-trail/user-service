package com.pawtrail.user.domain.provider.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 다른 서비스에서 받아온 후기 하나입니다.
 *
 * 하루 요약의 재료로만 씁니다.
 * 화면에 그대로 나가는 값이 아니라 언어 모델에게 넘길 재료라
 * 카드에 필요한 것이 아니라 문장에 쓰일 것만 담았습니다.
 *
 * 빼기로 한 것들이 있습니다.
 *   세부 점수 셋   rating 하나로 충분하고 그 결은 본문에 이미 들어 있습니다.
 *   사진          모델이 이미지를 보지 않습니다.
 *   좋아요 수      요약과 무관합니다.
 *   체중·크기      문장에 들어갈 자리가 없습니다.
 *   태그          명세에 어떤 값들인지가 없어 스텁 더미를 지어내야 합니다.
 *                review 를 만들 때 실제 목록과 어긋날 수 있어 뺐습니다.
 *
 * @param reviewId        후기 식별자입니다. 지금 쓰지 않지만 없으면 나중에 형태가 바뀝니다.
 * @param placeId         어느 장소의 후기인지입니다. 방문 기록의 장소와 맞춥니다.
 * @param visitedAt       방문 일자입니다. 사용자가 달력에서 고르는 값이라 시각이 없습니다.
 * @param rating          최종 별점 1~5 입니다.
 * @param content         후기 본문입니다. 요약 재료의 핵심입니다.
 * @param petBreedAtVisit 작성 시점의 견종 이름입니다.
 *                        반려동물 서비스가 아직 없어 이름은 쓸 수 없지만,
 *                        이 값은 후기에 스냅샷으로 박혀 있어 "말티즈와 함께" 정도는 됩니다.
 */
public record ReviewData(UUID reviewId,
                         UUID placeId,
                         LocalDate visitedAt,
                         Integer rating,
                         String content,
                         String petBreedAtVisit) {
}
