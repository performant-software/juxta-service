truncate table juxta_schema_version;
insert into juxta_schema_version (major, minor, micro) values ( '1','7,','10');

alter table juxta_collation_cache add permanent bool not null default 0;