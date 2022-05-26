CREATE TABLE IF NOT EXISTS SPECIFICATIONS (
    id                  VARCHAR(60)         DEFAULT RANDOM_UUID()           PRIMARY KEY,
    specification_name  VARCHAR(100)        NOT NULL
);

CREATE TABLE IF NOT EXISTS SPECIFICATION_ROWS (
    id                  VARCHAR(60)         DEFAULT RANDOM_UUID()           PRIMARY KEY,
    specification_id    VARCHAR(60),
    seg_group           VARCHAR(6),
    depth_5             VARCHAR(1),
    depth_4             VARCHAR(1),
    depth_3             VARCHAR(1),
    depth_2             VARCHAR(1),
    depth_1             VARCHAR(1),
    segment             VARCHAR(3),
    element             VARCHAR(4),
    sub_element         VARCHAR(4),
    component           VARCHAR(2),
    field_name          VARCHAR(100),
    arsecode            VARCHAR(8000),
    field_count         VARCHAR(4),
    looping_logic       VARCHAR(200),
    comments            VARCHAR(1000),
    row_index           INTEGER(8),
    CONSTRAINT FK_specification_rows_specification FOREIGN KEY (specification_id) REFERENCES SPECIFICATIONS(id)
);