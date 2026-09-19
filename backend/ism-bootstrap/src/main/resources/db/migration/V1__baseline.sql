CREATE TABLE ism_schema_marker (
    id BIGINT NOT NULL,
    marker_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schema_marker_code (marker_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ism_schema_marker (id, marker_code)
VALUES (1, 'B0_BASELINE');
