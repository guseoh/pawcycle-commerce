package com.pawcycle.backend.subscription;

import java.time.LocalDate;

public record ScheduleProjection(long id, LocalDate scheduledDate) {}
