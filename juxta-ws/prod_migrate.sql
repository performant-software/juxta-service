truncate juxta_collation_cache
update juxta_comparison_set set status = "NOT_COLLATED"

alter table juxta_collation_cache change data_type data_type enum('HEATMAP', "SIDEBYSIDE", "HISTOGRAM", "CONDENSED_HEATMAP", "CRITICAL_APPARATUS", "EXPORT") not null

drop table juxta_alignment
DROP TABLE text_annotation_data;
DROP TABLE text_annotation;

CREATE TABLE IF NOT EXISTS juxta_annotation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  set_id  BIGINT NOT NULL,
  witness_id  BIGINT NOT NULL,
  text_id bigint(20) NOT NULL,
  qname_id bigint(20) NOT NULL,
  range_start bigint(20) NOT NULL,
  range_end bigint(20) NOT NULL,
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
    group_num INT NOT NULL default 0,
    manual BOOL not null default 0,
    edit_distance INT NOT NULL DEFAULT -1,
    annotation_a_id BIGINT NOT NULL,
    annotation_b_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (set_id) REFERENCES juxta_comparison_set (id) ON DELETE CASCADE,
    FOREIGN KEY (annotation_a_id) REFERENCES juxta_annotation (id) ON DELETE CASCADE,
    FOREIGN KEY (annotation_a_id) REFERENCES juxta_annotation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
