-- 검수 메모 — 레코드의 현재 검수 메모(최신 확정/반려 사유). 이력이 아니라 현재 상태이므로 레코드에 둔다.
alter table inspection_record add column memo varchar(1000);
