package com.pawcycle.backend.subscription.persistence.projection;

import java.time.LocalDate;

public record ScheduleProjection(long id, LocalDate scheduledDate) {}
