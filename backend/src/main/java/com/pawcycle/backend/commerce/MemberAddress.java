package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_addresses")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MemberAddress {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", insertable = false, updatable = false)
	private Member member;

	@Column(nullable = false, length = 100)
	private String name;
	@Column(name = "recipient_name", nullable = false, length = 100)
	private String recipientName;
	@Column(name = "recipient_phone", nullable = false, length = 30)
	private String recipientPhone;
	@Column(name = "postal_code", nullable = false, length = 20)
	private String postalCode;
	@Column(name = "address_line1", nullable = false, length = 255)
	private String addressLine1;
	@Column(name = "address_line2", length = 255)
	private String addressLine2;
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
