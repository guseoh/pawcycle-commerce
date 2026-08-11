CREATE INDEX idx_schedules_due_automation
    ON subscription_schedules (status, scheduled_date, id, subscription_id);
