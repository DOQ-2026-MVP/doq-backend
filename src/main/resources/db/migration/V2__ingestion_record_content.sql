-- ingestion_record: 구조화 9컬럼 제거 → 원문 content(jsonb) 도입.
-- 원문→구조화 필드 매핑은 인입이 아니라 후속 structuring에서 수행한다.
-- (제공 데이터셋은 개발용 throwaway라 데이터 이관 없이 단순 교체.)

alter table ingestion_record
    drop column doc_id,
    drop column source_type,
    drop column supplier,
    drop column raw_item_name,
    drop column spec,
    drop column unit,
    drop column price_before,
    drop column price_after,
    drop column effective_date,
    add column content jsonb not null;
