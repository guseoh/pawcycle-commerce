package com.pawcycle.backend.member.application;

import com.pawcycle.backend.member.domain.MemberRole;

public record AuthenticatedMemberPrincipal(Long memberId, MemberRole role) {

	public AuthenticatedMemberPrincipal(Long memberId) {
		this(memberId, MemberRole.USER);
	}
}
