package com.pawcycle.backend.subscription.v2;

import java.time.LocalDate;
import java.util.List;

final class V2SubscriptionData {
	private V2SubscriptionData() {}

	record Pet(long id, String name, String petType) {}

	record PlanVersion(
			long planId,
			String planName,
			Long currentPlanVersionId,
			String targetPetType,
			boolean onSale,
			LocalDate saleStartsOn,
			LocalDate saleEndsOn,
			long id,
			long packagePriceKrw,
			boolean migrationOnly) {}

	record Subscription(long id, long memberId, String status, long version, Long petId,
			int deliveryCycleWeeks, long currentSnapshotId) {}

	record Schedule(long id, LocalDate scheduledDate) {}

	record ProcessedSchedule(LocalDate scheduledDate, int deliveryCycleWeeks) {}

	record Snapshot(long id, long planVersionId, long packagePriceKrw, int deliveryCycleWeeks, List<Item> items) {}
	record Item(long skuId, int quantity) {}
	record ScheduleView(long id, LocalDate scheduledDate, String status, Long effectiveSnapshotId) {}
	record CommandHistory(String commandType, String occurredAt) {}
	record Page<T>(int page, int size, long total, List<T> items) {}
	record DetailProjection(java.util.Map<String, Object> body) {}

	record StoredIdempotencyResult(String fingerprint, int status, String bodyJson, String location, String etag) {}
}
