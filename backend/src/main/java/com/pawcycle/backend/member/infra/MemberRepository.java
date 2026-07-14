package com.pawcycle.backend.member.infra;

import com.pawcycle.backend.member.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT member FROM Member member WHERE member.email = :email")
	Optional<Member> findByEmailForUpdate(@Param("email") String email);
}
