
use juxta_ws;

INSERT INTO `juxta_schema_version` (`major`, `minor`, `micro`)
VALUES
   (1, 7, 10);

INSERT INTO `juxta_workspace` (`id`, `name`, `description`)
VALUES
   (1, 'public', 'Default public workspace');

INSERT INTO `text_qname` (`id`, `local_name`, `namespace`)
VALUES
   (1, 'add', 'http://www.tei-c.org/ns/1.0'),
   (2, 'addDel', 'http://juxtasoftware.org/ns'),
   (3, 'addSpan', 'http://www.tei-c.org/ns/1.0'),
   (4, 'change', 'http://juxtasoftware.org/ns'),
   (5, 'del', 'http://www.tei-c.org/ns/1.0'),
   (6, 'delSpan', 'http://www.tei-c.org/ns/1.0'),
   (7, 'token', 'http://juxtasoftware.org/ns'),
   (8, 'transposition', 'http://juxtasoftware.org/ns'),
   (9, 'gap', 'http://juxtasoftware.org/ns');   
   
INSERT INTO `juxta_qname_filter` (`name`, `workspace_id`)
VALUES
   ('differences', 1),
   ('revisions', 1),
   ('tokens', 1),
   ('transpositions', 1);
      
INSERT INTO `juxta_qname_filter_member` (`filter_id`, `qname_id`)
VALUES
   (1, 2),
   (1, 4),
   (2, 6),
   (2, 4),
   (2, 3),
   (2, 5),
   (3, 7),
   (4, 8);

INSERT INTO `text_incrementer` (`text_qname_sequence`, `text_content_sequence`, `text_annotation_sequence`, `text_annotation_link_sequence`)
	VALUES
		(10, 0, 0, 0);

