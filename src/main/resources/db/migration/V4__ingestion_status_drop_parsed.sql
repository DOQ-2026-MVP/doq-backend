-- PARSED 상태 제거 (DRAFT / STRUCTURED / FAILED) — 검증은 업로드 시점 fail-fast + detection 플래그로 대체
alter table ingestion drop constraint ingestion_status_check;
alter table ingestion add constraint ingestion_status_check
    check (status in ('DRAFT', 'STRUCTURED', 'FAILED'));
