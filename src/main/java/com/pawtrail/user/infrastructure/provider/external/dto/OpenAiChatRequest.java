package com.pawtrail.user.infrastructure.provider.external.dto;

import java.util.List;
import java.util.Map;

/**
 * POST /v1/chat/completions 의 요청 본문입니다.
 *
 * Responses API 대신 이 엔드포인트를 쓰는 이유는 우리가 하는 일이
 * "한 번 부르고 문장 하나 받기" 뿐이기 때문입니다.
 * 대화 상태도 도구도 스트리밍도 쓰지 않아 그쪽의 이점이 하나도 쓰이지 않고,
 * 응답을 손으로 파싱하는 상황에서는 구조가 단순한 편이 낫습니다.
 *
 * @param model           모델 이름입니다.
 * @param reasoningEffort 추론에 얼마나 힘을 쓸지입니다.
 *                        기본값이 medium 인데 이 작업에는 과합니다.
 *                        추론 토큰은 출력으로 과금되고 응답도 그만큼 느려집니다.
 * @param messages        지시와 재료입니다. 첫째가 규칙이고 둘째가 그날 데이터입니다.
 * @param responseFormat  출력 형식입니다. JSON 스키마로 모양을 강제합니다.
 */
public record OpenAiChatRequest(String model,
                                String reasoningEffort,
                                List<Message> messages,
                                Map<String, Object> responseFormat) {

    /**
     * 메시지 하나입니다.
     *
     * @param role    system 이면 규칙, user 면 재료입니다.
     * @param content 내용입니다.
     */
    public record Message(String role, String content) {
    }
}
