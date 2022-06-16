CREATE TABLE IF NOT EXISTS SPECIFICATIONS (
    id                  VARCHAR(60)         DEFAULT RANDOM_UUID()           PRIMARY KEY,
    specification_name  VARCHAR(100)        NOT NULL,
    message_type        VARCHAR(10),
    version             VARCHAR(5)
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
    looping_logic       VARCHAR(400),
    comments            VARCHAR(1000),
    error_code          INTEGER(4),
    row_index           INTEGER(8),
    CONSTRAINT FK_specification_rows_specification FOREIGN KEY (specification_id) REFERENCES SPECIFICATIONS(id)
);

CREATE TABLE IF NOT EXISTS MASS_EDIT_QUERIES (
    id                  VARCHAR(60)         DEFAULT RANDOM_UUID()           PRIMARY KEY,
    type_lim            VARCHAR(20),
    occurrence          INTEGER(11),
    version_lim         VARCHAR(20),
    seg_group_in        VARCHAR(4),
    segment_in          VARCHAR(3),
    element_in          VARCHAR(4),
    sub_element_in      VARCHAR(4),
    component_in        VARCHAR(2),
    field_name_in       VARCHAR(100),
    arsecode_in         VARCHAR(8000),
    field_count_in      VARCHAR(4),
    looping_logic_in    VARCHAR(400),
    seg_group_out       VARCHAR(4),
    segment_out         VARCHAR(3),
    element_out         VARCHAR(4),
    sub_element_out     VARCHAR(4),
    component_out       VARCHAR(2),
    field_name_out      VARCHAR(100),
    arsecode_out        VARCHAR(8000),
    field_count_out     VARCHAR(4),
    looping_logic_out   VARCHAR(400)
);

CREATE TABLE IF NOT EXISTS VARIABLES (
    id                  VARCHAR(60)         DEFAULT RANDOM_UUID()           PRIMARY KEY,
    description         VARCHAR(200),
    var_name            VARCHAR(100),
    code                VARCHAR(300),
    var_type            VARCHAR(50),
    null_check          VARCHAR(25),
    var_params          VARCHAR(100),
    required_params     VARCHAR(200),
    additional_checks   VARCHAR(300)
);

CREATE TABLE IF NOT EXISTS CHANGES (
    id                  VARCHAR(60)         DEFAULT RANDOM_UUID()           PRIMARY KEY,
    query_id            VARCHAR(60),
    spec_row_id         VARCHAR(60),
    prev_seg_group      VARCHAR(6),
    prev_segment        VARCHAR(3),
    prev_element        VARCHAR(4),
    prev_sub_element    VARCHAR(4),
    prev_component      VARCHAR(2),
    prev_field_name     VARCHAR(100),
    prev_arsecode       VARCHAR(8000),
    prev_field_count    VARCHAR(4),
    prev_looping_logic  VARCHAR(400),
    CONSTRAINT FK_changes_mass_edit_queries FOREIGN KEY (query_id) REFERENCES MASS_EDIT_QUERIES(id),
    CONSTRAINT FK_changes_specification_rows FOREIGN KEY (spec_row_id) REFERENCES SPECIFICATION_ROWS(id)
);