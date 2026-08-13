-- 업로드 처리 현황(status) 추가 — 입력 세션 화면의 "업로드 현황" 표시용.
-- 컬럼 타입은 Hibernate(PostgreSQLDialect) 생성 스키마 기준 (ddl-auto=validate 통과용).

alter table ingestion_upload
    add column status varchar(255);

-- 기존 행 backfill — 지금까지 저장된 업로드는 전부 파싱 완료된 취합 파일이다.
update ingestion_upload
set status = case when type = 'BATCH_FILE' then 'PARSED' else 'PENDING_EXTRACTION' end
where status is null;

alter table ingestion_upload
    alter column status set not null;

alter table ingestion_upload
    add constraint ck_ingestion_upload_status check (status in ('PARSED', 'PENDING_EXTRACTION'));
