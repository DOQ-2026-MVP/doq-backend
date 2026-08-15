-- record_id 이름 정리 — 한 이름이 서로 다른 두 테이블을 가리키고 있었다.
--
--   inspection_record.record_id     → 인입 원본 행(ingestion_record.id)   ⇒ ingestion_record_id
--   inspection_changelog.record_id  → 검수 레코드(inspection_record.id)   ⇒ inspection_record_id
--
-- 특히 inspection_record 는 자신의 PK(id)와 나란히 record_id 를 갖고 있어, 레코드 단위 검수 API에
-- 어느 쪽을 넘겨야 하는지가 이름만 봐서는 구분되지 않았다.
-- 컬럼 타입은 Hibernate(PostgreSQLDialect) 생성 스키마 기준 (ddl-auto=validate 통과용).
--
-- rename column 은 그 컬럼에 걸린 인덱스·FK 제약을 그대로 따라가므로 재생성이 필요 없다.
-- (인덱스/제약 이름은 컬럼명을 담고 있지 않아 그대로 두어도 어긋나지 않는다)

alter table inspection_record
    rename column record_id to ingestion_record_id;

alter table inspection_changelog
    rename column record_id to inspection_record_id;
