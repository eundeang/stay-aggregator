-- Case 2: room_type_mapping을 hotel_mapping_id 단일 FK로 전환 (expand 단계).
-- 기존 (supplier, external_hotel_code) 컬럼과 그 위의 FK/UNIQUE는 이번 마이그레이션에서
-- 제거하지 않는다 — 아직 legacy 컬럼을 참조하는 코드가 없어야 안전하게 지울 수 있는데,
-- 이번 커밋에서 배치 upsert 쓰기 경로도 함께 hotel_mapping_id를 쓰도록 바꾸지만
-- "제거"는 그 경로가 하드닝까지 끝난 뒤(Case 3)로 미룬다. 근거: JOURNAL.md 해당 Day.
ALTER TABLE room_type_mapping
    ADD COLUMN hotel_mapping_id BIGINT NULL AFTER id;

UPDATE room_type_mapping rtm
    JOIN hotel_mapping hm
        ON hm.supplier = rtm.supplier AND hm.external_hotel_code = rtm.external_hotel_code
    SET rtm.hotel_mapping_id = hm.id;

-- 이제부터 JPA도, 배치 upsert의 새 SQL도 supplier/external_hotel_code를 쓰지 않는다
-- (hotel_mapping_id로 대체됐기 때문). 컬럼 자체는 legacy 삭제(Case 3)까지 남겨두되,
-- 더 이상 아무도 채우지 않으므로 NOT NULL을 유지하면 모든 INSERT가 즉시 실패한다.
ALTER TABLE room_type_mapping
    MODIFY COLUMN hotel_mapping_id BIGINT NOT NULL,
    MODIFY COLUMN supplier VARCHAR(20) NULL,
    MODIFY COLUMN external_hotel_code VARCHAR(100) NULL,
    ADD CONSTRAINT fk_room_type_mapping_hotel_id
        FOREIGN KEY (hotel_mapping_id) REFERENCES hotel_mapping (id),
    ADD CONSTRAINT uq_room_type_mapping_hotel_id
        UNIQUE (hotel_mapping_id, external_room_type_code);
