package com.pawcycle.backend.member.application;

import com.pawcycle.backend.member.domain.MemberRole;
import java.io.Serial;
import java.io.Serializable;

/** Stored in the HTTP session's SecurityContext, so it must stay serializable (AUTH-005). */
public record AuthenticatedMemberPrincipal(Long memberId, MemberRole role) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public AuthenticatedMemberPrincipal(Long memberId) {
    this(memberId, MemberRole.USER);
  }
}
