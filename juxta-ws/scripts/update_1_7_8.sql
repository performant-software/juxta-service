truncate table juxta_schema_version;
insert into juxta_schema_version (major, minor, micro) values ( '1','7,','8');
truncate table juxta_collation_cache;
alter table juxta_collation_cache 
   modify column `data_type` enum('HEATMAP','SIDEBYSIDE','CONDENSED_HEATMAP','EDITION','EXPORT') NOT NULL;