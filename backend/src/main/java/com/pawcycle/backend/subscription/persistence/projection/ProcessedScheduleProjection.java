package com.pawcycle.backend.subscription.persistence.projection;

import java.time.LocalDate;

public record ProcessedScheduleProjection(LocalDate scheduledDate, int deliveryCycleWeeks) {}
