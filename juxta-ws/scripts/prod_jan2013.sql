alter table juxta_comparison_set change `status` `status` ENUM('NOT_COLLATED','TOKENIZING','TOKENIZED','COLLATING','COLLATED','ERROR') not null default 'NOT_COLLATED';
alter table juxta_collation_cache change data_type data_type enum('HEATMAP', "SIDEBYSIDE", "HISTOGRAM", "CONDENSED_HEATMAP", "EDITION", "EXPORT") not null;

CREATE TABLE IF NOT EXISTS juxta_page_mark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    witness_id BIGINT NOT NULL,
    offset BIGINT NOT NULL,
    label TEXT,
    mark_type enum('PAGE_BREAK','LINE_NUMBER') not null default 'PAGE_BREAK',
    PRIMARY KEY (id),
    FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into juxta_page_mark (witness_id,offset,label) select witness_id,offset,label from juxta_page_break;

drop table juxta_page_break;
