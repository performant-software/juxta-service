
use juxta_ws;


--
-- Create new workspaces table
--
CREATE TABLE juxta_workspace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    PRIMARY KEY (id),
    UNIQUE (name),
    INDEX(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Add the default PUBLIC workspace
--
insert into juxta_workspace (name, description) values ('public', 'Default public workspace');

-- hack
delete from juxta_comparison_set where name = 'damozel.jxt';
delete from juxta_comparison_set where name = 'merlin';

-- 
-- find all of the unused, duplicate witness/sources and delete
--
delete jw from juxta_witness as jw 
	left join juxta_comparison_set_member on witness_id = id where witness_id is null;
delete js from juxta_source as js	
	left join juxta_witness on source_id = js.id where source_id is null;

--
-- add new constraints
--
alter table juxta_source add column workspace_id BIGINT NOT NULL default 1;
alter table juxta_source add FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE;
alter table juxta_source add UNIQUE INDEX(file_name, workspace_id);

alter table juxta_qname_filter drop INDEX name;
alter table juxta_qname_filter add column workspace_id BIGINT NOT NULL default 1;
alter table juxta_qname_filter add FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE;
alter table juxta_qname_filter add UNIQUE INDEX(name, workspace_id);

alter table juxta_profile drop INDEX name;
alter table juxta_profile add column workspace_id BIGINT NOT NULL default 1;
alter table juxta_profile add FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE;
alter table juxta_profile add UNIQUE INDEX(name, workspace_id);

alter table juxta_witness add column workspace_id BIGINT NOT NULL default 1;
alter table juxta_witness add FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE;
alter table juxta_witness add UNIQUE INDEX(name, workspace_id);


alter table juxta_comparison_set drop INDEX name;
alter table juxta_comparison_set add column workspace_id BIGINT NOT NULL default 1;
alter table juxta_comparison_set add FOREIGN KEY (workspace_id) REFERENCES juxta_workspace (id) ON DELETE CASCADE;
alter table juxta_comparison_set add UNIQUE INDEX(name, workspace_id);
