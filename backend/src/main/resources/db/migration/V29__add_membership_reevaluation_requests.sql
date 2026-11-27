-- PAYMENT-MEMBERSHIP-001: membership is re-evaluated outside the money transactions.
-- Payment success, reconciliation, subscription billing and refunds record one request per member
-- in their own transaction; a scheduled job evaluates the member in a separate transaction and
-- deletes the request. A request written while the job evaluates bumps request_version, so the job's
-- version-checked delete leaves it for the next run.
CREATE TABLE membership_reevaluation_requests (
    member_id BIGINT NOT NULL,
    request_version BIGINT NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_membership_reevaluation_requests PRIMARY KEY (member_id),
    -- A request has no meaning without its member; cascading keeps member removal unchanged.
    CONSTRAINT fk_membership_reevaluation_requests_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT chk_membership_reevaluation_requests_version CHECK (request_version >= 1),
    CONSTRAINT chk_membership_reevaluation_requests_attempts CHECK (attempts >= 0),
    INDEX ix_membership_reevaluation_requests_due (next_attempt_at, member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
