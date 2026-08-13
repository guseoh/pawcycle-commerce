package com.pawcycle.backend.member.domain;

import com.pawcycle.backend.commerce.MemberAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	/** No cascade: address deletion first clears this reference inside its application transaction. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "default_address_id")
	private MemberAddress defaultAddress;

	public Member(String email, String passwordHash) {
		this(email, passwordHash, MemberRole.USER);
	}

	public Member(String email, String passwordHash, MemberRole role) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
	}
}
