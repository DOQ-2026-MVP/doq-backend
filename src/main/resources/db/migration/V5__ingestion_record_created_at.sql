-- 원본 행 생성 시각 — 입력 화면이 수기 행 목록을 언제 넣은 순서로 보여주기 위해 필요하다.
-- 컬럼 타입은 Hibernate(PostgreSQLDialect) 생성 스키마 기준 (ddl-auto=validate 통과용).

alter table ingestion_record
    add column created_at timestamp(6);

-- 기존 행 backfill — 행 단위 시각을 남긴 적이 없으니 속한 세션의 생성 시각으로 채운다.
update ingestion_record r
set created_at = i.created_at
from ingestion i
where i.id = r.ingestion_id
  and r.created_at is null;

alter table ingestion_record
    alter column created_at set not null;
