package com.pawtrail.user.infrastructure.persistence.jpa;

import com.pawtrail.user.domain.model.DailySummary;
import com.pawtrail.user.domain.model.DailySummaryId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 스프링 데이터가 구현체를 만들어 주는 인터페이스입니다.
 * 이 파일은 도메인이 보지 않습니다.
 *
 * 식별자 타입이 UUID 가 아니라 DailySummaryId 입니다.
 * 이 표만 기본 키가 둘이기 때문입니다.
 */
public interface DailySummaryJpaRepository extends JpaRepository<DailySummary, DailySummaryId> {
}
