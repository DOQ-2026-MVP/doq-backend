-- 원본유형 어휘 통일 — 확장자 표기(PNG·JPG·JPEG)를 범주(IMAGE)로 옮긴다.
--
-- 분류기가 문서 포맷명을 원본유형으로 그대로 흘려서 이미지 원본이 "PNG"·"JPEG" 로 쌓였다.
-- 화면이 쓰는 어휘는 범주(XLSX·CSV·PDF·IMAGE·MANUAL)라 어느 선택지에도 걸리지 않았고,
-- 검수 상세의 원본유형 select 가 매칭되는 항목을 못 찾아 첫 옵션(XLSX)으로 떨어졌다.
--
-- 새로 들어오는 값은 코드(SourceType·DocumentFormat)가 막는다. 여기서는 이미 쌓인 행을 옮긴다.
-- 정확한 포맷이 필요하면 ingestion_upload.file_name 이 그대로 들고 있으므로 잃는 정보는 없다.
--
-- jsonb 라 문자열 치환이 아니라 키 단위로 바꾼다 — 다른 필드에 들어간 같은 글자를 건드리지 않게.

-- 인입 원본 행. content.values 의 키는 경로마다 다르다 — 문서 추출·수기는 영문 키(sourceType),
-- 취합 표 파일은 원본 헤더(원본유형) 그대로다. 'sourceType' 키만 짚으므로 시스템이 부여한 값만
-- 옮겨가고, 사용자가 표에 적어 넣은 원문은 손대지 않는다(관찰값은 원문 그대로가 이 시스템의 규칙).
update ingestion_record
set content = jsonb_set(content, '{values,sourceType}', '"IMAGE"')
where upper(content #>> '{values,sourceType}') in ('PNG', 'JPG', 'JPEG');

-- 검수 레코드. observed·current_values 는 매핑을 거친 MappedRecord 라 어느 경로에서 왔든
-- sourceType 키로 평평하게 담긴다. 검수 화면이 읽고 고치는 사본이므로 여기서는 어휘를 맞춘다.
update inspection_record
set observed = jsonb_set(observed, '{sourceType}', '"IMAGE"')
where upper(observed ->> 'sourceType') in ('PNG', 'JPG', 'JPEG');

update inspection_record
set current_values = jsonb_set(current_values, '{sourceType}', '"IMAGE"')
where upper(current_values ->> 'sourceType') in ('PNG', 'JPG', 'JPEG');
