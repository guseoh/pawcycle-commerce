package com.pawcycle.backend.commerce;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {
	List<MemberAddress> findByMemberIdOrderById(long memberId);
	Optional<MemberAddress> findByIdAndMemberId(long id, long memberId);
}
