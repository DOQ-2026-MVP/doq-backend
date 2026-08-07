-- 세션 상태에 STRUCTURED 추가 (DRAFT / PARSED / STRUCTURED / FAILED)
alter table ingestion drop constraint ingestion_status_check;
alter table ingestion add constraint ingestion_status_check
    check (status in ('DRAFT', 'PARSED', 'STRUCTURED', 'FAILED'));
