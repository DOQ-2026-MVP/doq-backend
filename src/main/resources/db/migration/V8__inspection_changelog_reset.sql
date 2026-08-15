-- 검수 레코드 초기화(RESET) 이력 — 변경 유형에 RESET 을 추가한다.
--
-- 초기화는 편집본을 관찰값으로 되돌리고 NEW 로 전이하는 한 번의 변경이라, 편집(EDIT)도 전이(CONFIRM/REJECT)도
-- 아닌 자기 이름이 필요하다. 이력에는 전이(from→NEW)와 되돌린 필드 diff 가 함께 남는다.
--
-- type 의 check 제약은 V2 에서 컬럼에 인라인으로 걸려 Postgres 가 자동 명명했다(<table>_<column>_check).
-- 값 목록만 바꾸는 방법이 없으므로 떼고 다시 건다. 기존 행은 모두 새 목록에 포함되니 재검증도 통과한다.

alter table inspection_changelog
    drop constraint inspection_changelog_type_check;

alter table inspection_changelog
    add constraint inspection_changelog_type_check
        check (type in ('EDIT', 'CONFIRM', 'REJECT', 'RESET'));
