alter table juxta_source add content_type ENUM('TXT','XML','HTML','WIKI') not null
update juxta_source set content_type='XML' where content_id in (select id from text_content where type = 1)
