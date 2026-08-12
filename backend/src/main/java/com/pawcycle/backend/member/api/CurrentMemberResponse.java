package com.pawcycle.backend.member.api;

import com.pawcycle.backend.member.domain.MemberRole;

public record CurrentMemberResponse(Long memberId, MemberRole role) {
}
