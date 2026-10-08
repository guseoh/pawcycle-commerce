package com.pawcycle.backend.subscription.persistence;

import com.pawcycle.backend.subscription.persistence.projection.PetProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Derived DTO queries select only the PetProjection constructor fields. */
interface PetReadRepository extends Repository<SubscriptionReadRows.Pet, Long> {
  Optional<PetProjection> findByMemberIdAndId(long memberId, long id);
  List<PetProjection> findByMemberIdOrderByIdAsc(long memberId, Pageable page);
  long countByMemberId(long memberId);
  List<PetProjection> findByMemberIdAndIdIn(long memberId, List<Long> ids);
}
