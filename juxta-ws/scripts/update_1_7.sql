CREATE TABLE juxta_schema_version (
	major tinyint unsigned not null,
	minor tinyint unsigned not null
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;
insert into juxta_schema_version (major,minor) values (1,7);

CREATE TABLE `juxta_comparison_note` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `set_id` bigint(20) NOT NULL,
  `base_id` bigint(20) NOT NULL,
  `witness_id` bigint(20) NOT NULL,
  `range_start` MEDIUMINT UNSIGNED NOT NULL,
  `range_end` MEDIUMINT UNSIGNED NOT NULL,
  `note` text NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (base_id) REFERENCES juxta_witness (id) ON DELETE CASCADE,
  FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table juxta_alignment  
	modify column group_num SMALLINT UNSIGNED NOT NULL default 0,
	modify column edit_distance TINYINT NOT NULL DEFAULT -1;
	
alter table juxta_annotation  
	modify column range_start MEDIUMINT UNSIGNED NOT NULL,
	modify column range_end MEDIUMINT UNSIGNED NOT NULL;
	
alter table juxta_comparison_set_member
	modify column tokenized_length MEDIUMINT UNSIGNED  DEFAULT '0';
	
alter table juxta_note
	modify column anchor_start  MEDIUMINT UNSIGNED NOT NULL,
	modify column anchor_end  MEDIUMINT UNSIGNED NOT NULL;
	
alter table juxta_page_mark
	modify column offset  MEDIUMINT UNSIGNED NOT NULL;
	
alter table juxta_revision
	modify column `start`  MEDIUMINT UNSIGNED NOT NULL,
	modify column `end`  MEDIUMINT UNSIGNED NOT NULL;
		
alter table juxta_witness 
	drop  fragment_start,
	drop fragment_end;
