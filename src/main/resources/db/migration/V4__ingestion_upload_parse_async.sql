-- 업로드 후속 처리(취합 파일 파싱·원본 문서 추출)를 업로드 응답 이후 비동기로 돌린다.
-- 접수 직후 PARSING → 결과에 따라 PARSED / PARSE_FAILED 로 끝난다.
-- 응답이 이미 나간 뒤라 실패를 400으로 알릴 수 없으므로 실패도 상태로 남긴다(사유 포함).

alter table ingestion_upload
    drop constraint ck_ingestion_upload_status;

-- 보관 전용 문서에 쓰던 PENDING_EXTRACTION 은 PARSED(행 0건)로 흡수한다 —
-- 화면 입장에서는 "기계가 이 파일에 대해 할 일이 끝났다"로 같고, 추출이 붙어도 상태가 늘지 않는다.
update ingestion_upload
set status = 'PARSED'
where status = 'PENDING_EXTRACTION';

alter table ingestion_upload
    add constraint ck_ingestion_upload_status
        check (status in ('PARSING', 'PARSED', 'PARSE_FAILED'));

-- 실패 사유 — PARSE_FAILED 일 때만 채워진다. 길이는 엔티티(@Column(length=500))와 맞춘다.
alter table ingestion_upload
    add column failure_reason varchar(500);
