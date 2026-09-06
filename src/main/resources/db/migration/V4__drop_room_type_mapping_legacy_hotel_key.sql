-- Case 3 하드닝(contract 단계): hotel_mapping_id 전환(V3)이 쓰기 경로까지 끝났고
-- 아무 코드도 이제 room_type_mapping.supplier/external_hotel_code를 참조하지
-- 않는다 — legacy 복합 FK/UNIQUE와 컬럼 자체를 제거해 최종 스키마를
-- docs/architecture.md·CLAUDE.md가 서술하는 목표 설계와 일치시킨다.
ALTER TABLE room_type_mapping
    DROP FOREIGN KEY fk_room_type_mapping_hotel;

ALTER TABLE room_type_mapping
    DROP INDEX uq_room_type_mapping;

ALTER TABLE room_type_mapping
    DROP COLUMN supplier,
    DROP COLUMN external_hotel_code;
