package com.pawcycle.backend.member.address.application;

import com.pawcycle.backend.commerce.AddressRequest;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.member.address.api.AddressResponse;
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
  public List<AddressResponse> list(long memberId) {
    Member member = requireMember(memberId);
    Long defaultId =
        member.getDefaultAddress() == null ? null : member.getDefaultAddress().getId();
    return addresses.findByMemberIdOrderById(memberId).stream()
        .map(address -> response(address, defaultId))
        .toList();
  }

  @Transactional
  public long create(long memberId, AddressRequest request) {
    validate(request, true);
    Member member = requireMember(memberId);
    MemberAddress address = newAddress(memberId, request);
    addresses.saveAndFlush(address);
    if (member.getDefaultAddress() == null) {
      member.assignDefaultAddress(address);
      members.save(member);
    }
    subscriptionShipping.releaseAddressHolds(memberId);
    return address.getId();
  }

  @Transactional
  public void update(long memberId, long addressId, AddressRequest request) {
    validate(request, true);
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
    members.save(member);
    subscriptionShipping.releaseAddressHolds(memberId);
  }

  @Transactional
  public void updateSubscriptionShipping(
      long memberId, long subscriptionId, AddressRequest request) {
    validate(request, false);
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

  private MemberAddress newAddress(long memberId, AddressRequest request) {
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

  private static AddressResponse response(MemberAddress address, Long defaultId) {
    return new AddressResponse(
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

  private static void validate(AddressRequest request, boolean requireName) {
    if (requireName) validateRequiredLength(request.name(), "name", 100);
    validateRequiredLength(request.recipientName(), "recipientName", 100);
    validateRequiredLength(request.recipientPhone(), "recipientPhone", 30);
    validateRequiredLength(request.postalCode(), "postalCode", 20);
    validateRequiredLength(request.addressLine1(), "addressLine1", 255);
    if (request.addressLine2() != null && request.addressLine2().length() > 255) {
      throw new CommerceException(400, "VALIDATION_FAILED", "addressLine2 길이가 허용 범위를 초과했습니다.");
    }
  }

  private static void validateRequiredLength(String value, String key, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new CommerceException(400, "VALIDATION_FAILED", key + " is required");
    }
    if (value.length() > maxLength) {
      throw new CommerceException(400, "VALIDATION_FAILED", key + " 길이가 허용 범위를 초과했습니다.");
    }
  }

  private static CommerceException notFound(String code) {
    return new CommerceException(404, code, "요청한 리소스를 찾을 수 없습니다.");
  }
}
