ALTER TABLE project
    ADD COLUMN current_structure_version VARCHAR(64);

UPDATE project
SET current_structure_version = 'source-structure-v1'
WHERE current_revision IS NOT NULL;

ALTER TABLE index_task
    ADD COLUMN structure_version VARCHAR(64) NOT NULL DEFAULT 'source-structure-v1';

ALTER TABLE knowledge_document
    ADD COLUMN structure_version VARCHAR(64) NOT NULL DEFAULT 'source-structure-v1';
