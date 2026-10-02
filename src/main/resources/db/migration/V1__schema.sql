CREATE TABLE member (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 name VARCHAR(50) NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 email_verified_at DATETIME(6),
 membership VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
 role VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
 rejection_reason VARCHAR(500),
 reviewed_by BIGINT,
 reviewed_at DATETIME(6),
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_member_reviewer FOREIGN KEY (reviewed_by) REFERENCES member(id),
 CONSTRAINT ck_membership CHECK (membership IN ('NOT_REQUESTED','PENDING','APPROVED','REJECTED','EXPELLED')),
 CONSTRAINT ck_role CHECK (role IN ('MEMBER','ADMIN')),
 INDEX ix_member_role(role,id),
 INDEX ix_member_membership(membership,id)
) ENGINE=InnoDB;

CREATE TABLE email_verification (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL UNIQUE,
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 expires_at DATETIME(6) NOT NULL,
 used_at DATETIME(6),
 CONSTRAINT fk_verification_member FOREIGN KEY (member_id) REFERENCES member(id)
) ENGINE=InnoDB;

CREATE TABLE booking_week (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 week_start DATE NOT NULL UNIQUE,
 published BOOLEAN NOT NULL DEFAULT FALSE,
 automatic BOOLEAN NOT NULL DEFAULT FALSE,
 opens_at DATETIME(6) NOT NULL,
 closes_at DATETIME(6) NOT NULL,
 CONSTRAINT ck_week_window CHECK (opens_at < closes_at)
) ENGINE=InnoDB;

CREATE TABLE booking_slot (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 week_id BIGINT NOT NULL,
 start_at DATETIME(6) NOT NULL UNIQUE,
 end_at DATETIME(6) NOT NULL,
 blocked BOOLEAN NOT NULL DEFAULT FALSE,
 block_reason VARCHAR(500),
 CONSTRAINT fk_slot_week FOREIGN KEY (week_id) REFERENCES booking_week(id),
 CONSTRAINT ck_slot_duration CHECK (TIMESTAMPDIFF(SECOND,start_at,end_at) = 1800),
 INDEX ix_slot_week_start(week_id,start_at)
) ENGINE=InnoDB;

CREATE TABLE reservation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL,
 week_id BIGINT NOT NULL,
 start_at DATETIME(6) NOT NULL,
 end_at DATETIME(6) NOT NULL,
 rehearsal_name VARCHAR(100) NOT NULL,
 reservation_type VARCHAR(32) NOT NULL DEFAULT 'TEAM_REHEARSAL',
 status VARCHAR(10) NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 cancelled_at DATETIME(6),
 cancelled_by BIGINT,
 cancel_reason VARCHAR(500),
 CONSTRAINT fk_res_member FOREIGN KEY (member_id) REFERENCES member(id),
 CONSTRAINT fk_res_week FOREIGN KEY (week_id) REFERENCES booking_week(id),
 CONSTRAINT fk_res_canceller FOREIGN KEY (cancelled_by) REFERENCES member(id),
 CONSTRAINT ck_res_time CHECK (start_at < end_at),
 CONSTRAINT ck_res_status CHECK (status IN ('ACTIVE','CANCELLED')),
 CONSTRAINT ck_reservation_type CHECK (reservation_type IN ('TEAM_REHEARSAL','PERSONAL_PRACTICE','LESSON')),
 CONSTRAINT uq_res_request UNIQUE(member_id,request_key),
 INDEX ix_res_member_start(member_id,start_at,id),
 INDEX ix_res_week_start(week_id,start_at,id)
) ENGINE=InnoDB;

CREATE TABLE active_slot_claim (
 slot_id BIGINT NOT NULL PRIMARY KEY,
 reservation_id BIGINT NOT NULL,
 CONSTRAINT fk_claim_slot FOREIGN KEY (slot_id) REFERENCES booking_slot(id),
 CONSTRAINT fk_claim_res FOREIGN KEY (reservation_id) REFERENCES reservation(id),
 INDEX ix_claim_reservation(reservation_id)
) ENGINE=InnoDB;

CREATE TABLE announcement_delivery (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 notice_date DATE NOT NULL UNIQUE,
 status VARCHAR(15) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL,
 started_at DATETIME(6),
 message_id VARCHAR(40),
 error_summary VARCHAR(200),
 CONSTRAINT ck_delivery_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED','UNKNOWN')),
 INDEX ix_delivery_due(status,next_attempt_at)
) ENGINE=InnoDB;
