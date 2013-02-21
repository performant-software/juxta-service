CREATE TABLE  IF NOT EXISTS juxta_schema_version (
	major tinyint unsigned not null,
	minor tinyint unsigned not null,
	micro tinyint unsigned not null
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;
insert into juxta_schema_version (major,minor,micro) values (1,7,7);

CREATE TABLE IF NOT EXISTS juxta_user_note (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  set_id bigint(20) NOT NULL,
  base_id bigint(20) NOT NULL,
  range_start mediumint(8) unsigned NOT NULL,
  range_end mediumint(8) unsigned NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (base_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS juxta_user_note_data (
  note_id bigint(20) NOT NULL,
  witness_id bigint(20) NOT NULL,
  note text NOT NULL,
  PRIMARY KEY (note_id, witness_id),
  FOREIGN KEY (note_id) REFERENCES juxta_user_note (id) ON DELETE CASCADE,
  FOREIGN KEY (witness_id) REFERENCES juxta_witness (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table juxta_alignment  
	modify column group_num SMALLINT UNSIGNED NOT NULL default 0,
	modify column edit_distance SMALLINT NOT NULL DEFAULT -1;
	
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
	
alter table juxta_comparison_set 
   modify column `status` enum('NOT_COLLATED','TOKENIZING','TOKENIZED','COLLATING','COLLATED','ERROR','DELETED') NOT NULL DEFAULT 'NOT_COLLATED';
