package com.pawcycle.backend.member.address.application;

import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.domain.MemberAddress;
import com.pawcycle.backend.member.persistence.MemberAddressRepository;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.subscription.persistence.SubscriptionShippingPersistenceAdapter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAddressApplicationService {
  private final MemberRepository members;
  private final MemberAddressRepository addresses;
  private final SubscriptionShippingPersistenceAdapter subscriptionShipping;
  private final Clock clock;

  public MemberAddressApplicationService(
      MemberRepository members,
      MemberAddressRepository addresses,
      SubscriptionShippingPersistenceAdapter subscriptionShipping,
      Clock clock) {
    this.members = members;
    this.addresses = addresses;
    this.subscriptionShipping = subscriptionShipping;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AddressView> list(long memberId) {
    Member member = requireMember(memberId);
    Long defaultId = member.getDefaultAddress() == null ? null : member.getDefaultAddress().getId();
    return addresses.findByMemberIdOrderById(memberId).stream()
        .map(address -> response(address, defaultId))
        .toList();
  }

  @Transactional
  public long create(long memberId, MemberAddressCommand request) {
    Member member = requireMember(memberId);
    MemberAddress address = newAddress(memberId, request);
    addresses.saveAndFlush(address);
    if (member.getDefaultAddress() == null) {
      member.assignDefaultAddress(address);
      members.saveAndFlush(member);
    }
    subscriptionShipping.releaseAddressHolds(memberId);
    return address.getId();
  }

  @Transactional
  public void update(long memberId, long addressId, MemberAddressCommand request) {
    requireMember(memberId);
    MemberAddress address =
        addresses
            .findByIdAndMemberId(addressId, memberId)
            .orElseThrow(() -> notFound("ADDRESS_NOT_FOUND"));
    address.update(
        request.name(),
        request.recipientName(),
        request.recipientPhone(),
        request.postalCode(),
        request.addressLine1(),
        request.addressLine2(),
        now());
    subscriptionShipping.releaseAddressHolds(memberId);
  }

  @Transactional
  public void delete(long memberId, long addressId) {
    Member member = requireMember(memberId);
    MemberAddress address =
        addresses
            .findByIdAndMemberId(addressId, memberId)
            .orElseThrow(() -> notFound("ADDRESS_NOT_FOUND"));
    if (member.getDefaultAddress() != null
        && Long.valueOf(addressId).equals(member.getDefaultAddress().getId())) {
      member.clearDefaultAddress();
      members.save(member);
    }
    addresses.delete(address);
  }

  @Transactional
  public void makeDefault(long memberId, long addressId) {
    Member member = requireMember(memberId);
    MemberAddress address =
        addresses
            .findByIdAndMemberId(addressId, memberId)
            .orElseThrow(() -> notFound("ADDRESS_NOT_FOUND"));
    member.assignDefaultAddress(address);
    members.saveAndFlush(member);
    subscriptionShipping.releaseAddressHolds(memberId);
  }

  @Transactional
  public void updateSubscriptionShipping(
      long memberId, long subscriptionId, SubscriptionShippingAddressCommand request) {
    requireMember(memberId);
    subscriptionShipping.update(
        memberId,
        subscriptionId,
        new SubscriptionShippingPersistenceAdapter.ShippingAddress(
            request.recipientName(),
            request.recipientPhone(),
            request.postalCode(),
            request.addressLine1(),
            request.addressLine2()));
  }

  private Member requireMember(long memberId) {
    return members.findById(memberId).orElseThrow(() -> notFound("MEMBER_NOT_FOUND"));
  }

  private MemberAddress newAddress(long memberId, MemberAddressCommand request) {
    return new MemberAddress(
        memberId,
        request.name(),
        request.recipientName(),
        request.recipientPhone(),
        request.postalCode(),
        request.addressLine1(),
        request.addressLine2(),
        now());
  }

  private static AddressView response(MemberAddress address, Long defaultId) {
    return new AddressView(
        address.getId(),
        address.getName(),
        address.getRecipientName(),
        address.getRecipientPhone(),
        address.getPostalCode(),
        address.getAddressLine1(),
        address.getAddressLine2(),
        address.getId().equals(defaultId));
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private static MemberAddressException notFound(String code) {
    return new MemberAddressException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }
}
