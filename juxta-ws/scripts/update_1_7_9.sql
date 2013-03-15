truncate table juxta_schema_version;
insert into juxta_schema_version (major, minor, micro) values ( '1','7,','9');
truncate table juxta_collation_cache;   
   
CREATE TABLE IF NOT EXISTS juxta_user_note_data_new (
  note_id bigint(20) NOT NULL,
  witness_id bigint(20) default null default 0,
  note text NOT NULL,
  key (note_id),
  FOREIGN KEY (note_id) REFERENCES juxta_user_note (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into juxta_user_note_data_new (note_id,witness_id,note) select note_id,witness_id,note from juxta_user_note_data;
drop table juxta_user_note_data;
rename table juxta_user_note_data_new to juxta_user_note_data;


alter table juxta_user_note add group_id bigint(20) default null;
alter table juxta_user_note_data add id BIGINT PRIMARY KEY AUTO_INCREMENT;
alter table juxta_user_note_data add is_group BOOL NOT NULL DEFAULT 0;
