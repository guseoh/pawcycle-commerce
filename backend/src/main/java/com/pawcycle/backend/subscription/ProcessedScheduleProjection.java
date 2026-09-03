package com.pawcycle.backend.subscription;

import java.time.LocalDate;

public record ProcessedScheduleProjection(LocalDate scheduledDate, int deliveryCycleWeeks) {}
