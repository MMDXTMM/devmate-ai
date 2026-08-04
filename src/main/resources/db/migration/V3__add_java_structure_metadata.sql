ALTER TABLE knowledge_document
    ADD COLUMN package_name VARCHAR(500);

ALTER TABLE knowledge_chunk
    ADD COLUMN metadata_json TEXT;
