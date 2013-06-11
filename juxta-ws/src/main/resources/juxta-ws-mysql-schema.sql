CREATE TABLE IF NOT EXISTS juxta_schema_version (
   major tinyint unsigned not null,
   minor tinyint unsigned not null,
   micro tinyint unsigned not null
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_workspace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    PRIMARY KEY (id),
    UNIQUE (name),
    INDEX(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    content_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL default 1,
    content_type ENUM('TXT','XML','HTML','WIKI') not null,
    created DATETIME not null,
    updated DATETIME,
    PRIMARY KEY (id),
    FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES text_content (id),
    UNIQUE INDEX(name, workspace_id),
    INDEX (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_qname_filter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    workspace_id BIGINT NOT NULL default 1,
    PRIMARY KEY (id),
    FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE,
    UNIQUE INDEX(name, workspace_id),
    INDEX(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_qname_filter_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    filter_id BIGINT NOT NULL,
    qname_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (filter_id) REFERENCES juxta_qname_filter (id) ON DELETE CASCADE,
    FOREIGN KEY (qname_id) REFERENCES text_qname (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_xslt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL default 1,
    name VARCHAR(255) NOT NULL,
    xslt text not null,
    default_namespace VARCHAR(255),
    UNIQUE INDEX(name, workspace_id),
    PRIMARY KEY (id),
    FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_witness (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    source_id BIGINT NOT NULL,
    xslt_id BIGINT,
    text_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL default 1,
    created DATETIME not null,
    updated DATETIME,
    PRIMARY KEY(id),
    FOREIGN KEY (source_id) REFERENCES juxta_source (id) ON DELETE CASCADE,
    FOREIGN KEY (xslt_id) REFERENCES juxta_xslt (id),
    FOREIGN KEY (text_id) REFERENCES text_content (id),
    FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE,
    UNIQUE INDEX(name, workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    witness_id BIGINT NOT NULL,
    revision_type ENUM('ADD','DELETE') not null,
    start MEDIUMINT UNSIGNED NOT NULL,
    end MEDIUMINT UNSIGNED NOT NULL,
    content TEXT,
    is_included BOOL NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_comparison_set (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    workspace_id BIGINT NOT NULL default 1,
    status ENUM('NOT_COLLATED','TOKENIZING','TOKENIZED','COLLATING','COLLATED','ERROR','DELETED') not null default 'NOT_COLLATED',
    created DATETIME not null,
    updated DATETIME,
    PRIMARY KEY (id),
    FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE,
   UNIQUE INDEX(name, workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_collation_cache (
    id BIGINT NOT NULL AUTO_INCREMENT,
    set_id BIGINT NOT NULL,
    config VARCHAR(255) NOT NULL,
    data_type enum('HEATMAP', "SIDEBYSIDE", "HISTOGRAM", "CONDENSED_HEATMAP", "EDITION", "EXPORT") not null,
    data LONGTEXT,
    created TIMESTAMP not null,
    PRIMARY KEY (id),
    FOREIGN KEY (set_id) REFERENCES juxta_comparison_set (id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_collator_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    set_id BIGINT NOT NULL,
    filter_whitespace BOOL NOT NULL DEFAULT 1,
    filter_punctuation BOOL NOT NULL DEFAULT 1,
    filter_case BOOL NOT NULL DEFAULT 1,
    hyphenation_filter enum('INCLUDE_ALL','FILTER_LINEBREAK','FILTER_ALL') not null default 'INCLUDE_ALL',
    PRIMARY KEY (id),
    FOREIGN KEY (set_id) REFERENCES juxta_comparison_set (id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_comparison_set_member (
    set_id BIGINT NOT NULL,
    witness_id BIGINT NOT NULL,
    tokenized_length MEDIUMINT UNSIGNED default 0,
    UNIQUE (set_id, witness_id),
    FOREIGN KEY (set_id) REFERENCES juxta_comparison_set (id) ON DELETE CASCADE,
    FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    witness_id BIGINT NOT NULL,
    anchor_start MEDIUMINT UNSIGNED NOT NULL,
    anchor_end MEDIUMINT UNSIGNED NOT NULL,
    note_type VARCHAR(255),
    target_xml_id VARCHAR(255),
    content TEXT,
    PRIMARY KEY (id),
    FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_page_mark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    witness_id BIGINT NOT NULL,
    offset MEDIUMINT UNSIGNED NOT NULL,
    label TEXT,
    mark_type enum('PAGE_BREAK','LINE_NUMBER') not null default 'PAGE_BREAK',
    PRIMARY KEY (id),
    FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_annotation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  set_id  BIGINT NOT NULL,
  witness_id  BIGINT NOT NULL,
  text_id bigint(20) NOT NULL,
  qname_id bigint(20) NOT NULL,
  range_start MEDIUMINT UNSIGNED NOT NULL,
  range_end MEDIUMINT UNSIGNED NOT NULL,
  manual BOOL not null default 0,
  PRIMARY KEY (id),
  FOREIGN KEY (set_id) REFERENCES juxta_comparison_set (id) ON DELETE CASCADE,
  FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE,
  FOREIGN KEY (text_id) REFERENCES text_content (id) ON DELETE CASCADE,
  FOREIGN KEY (qname_id) REFERENCES text_qname (id),
  KEY range_start (range_start,range_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE IF NOT EXISTS juxta_alignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    set_id  BIGINT NOT NULL,
    qname_id  BIGINT NOT NULL,
    group_num SMALLINT UNSIGNED NOT NULL default 0,
    manual BOOL not null default 0,
    edit_distance SMALLINT NOT NULL DEFAULT -1,
    annotation_a_id BIGINT NOT NULL,
    annotation_b_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (set_id) REFERENCES juxta_comparison_set (id) ON DELETE CASCADE,
    FOREIGN KEY (annotation_a_id) REFERENCES juxta_annotation (id) ON DELETE CASCADE,
    FOREIGN KEY (annotation_a_id) REFERENCES juxta_annotation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_user_note (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  set_id bigint(20) NOT NULL,
  base_id bigint(20) NOT NULL,
  range_start mediumint(8) unsigned NOT NULL,
  range_end mediumint(8) unsigned NOT NULL,
  group_id bigint(20) default null,
  PRIMARY KEY (id),
  FOREIGN KEY (base_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_user_note_data (
  id BIGINT NOT NULL AUTO_INCREMENT,
  note_id bigint(20) NOT NULL,
  witness_id bigint(20) NOT NULL,
  note text NOT NULL,
  is_group BOOL NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY (note_id),
  FOREIGN KEY (note_id) REFERENCES juxta_user_note (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace VARCHAR(255) NOT NULL,
    num_sources INT UNSIGNED not null default 0,
    max_src_size INT UNSIGNED not null default 0,
    min_src_size INT UNSIGNED not null default 0,
    mean_src_size INT UNSIGNED not null default 0,
    total_src_size INT UNSIGNED not null default 0,
    secs_collating INT UNSIGNED not null default 0,
    started_collations INT UNSIGNED not null default 0,
    finished_collations INT UNSIGNED not null default 0,
    max_set_witnesses INT UNSIGNED not null default 0,
    min_set_witnesses INT UNSIGNED not null default 0,
    mean_set_witnesses INT UNSIGNED not null default 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP TABLE text_annotation_link_data;
DROP TABLE text_annotation_link_target;
DROP TABLE text_annotation_link;
DROP TABLE text_annotation_data;
DROP TABLE text_annotation;