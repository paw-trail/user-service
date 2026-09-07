package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.model.Favorite;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.infrastructure.persistence.jpa.FavoriteJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class FavoriteRepositoryImpl implements FavoriteRepository {

    private final FavoriteJpaRepository favoriteJpaRepository;

    @Override
    public Favorite save(Favorite favorite) {
        return favoriteJpaRepository.save(favorite);
    }

    @Override
    public Optional<Favorite> findById(UUID id) {
        return favoriteJpaRepository.findById(id);
    }

    @Override
    public void delete(Favorite favorite) {
        favoriteJpaRepository.delete(favorite);
    }

    @Override
    public long countByAccountId(UUID accountId) {
        return favoriteJpaRepository.countByAccountId(accountId);
    }

    @Override
    public List<Favorite> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId) {
        return favoriteJpaRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Override
    public Optional<Favorite> findByAccountIdAndPlaceId(UUID accountId, UUID placeId) {
        return favoriteJpaRepository.findByAccountIdAndPlaceId(accountId, placeId);
    }

    @Override
    public Page<UUID> findAccountIdsByPlaceId(UUID placeId, Pageable pageable) {
        return favoriteJpaRepository.findAccountIdsByPlaceId(placeId, pageable);
    }
}
