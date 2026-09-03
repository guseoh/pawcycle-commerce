ALTER TABLE subscriptions
    CHANGE COLUMN mvp2_managed runtime_managed BOOLEAN NOT NULL DEFAULT FALSE;
