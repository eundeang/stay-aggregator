-- V1의 hotel_mapping PK는 (supplier, external_hotel_code) 복합키였다. 이 값을 그대로
-- API 응답의 숙소 식별자로 노출하면 공급사 내부 코드가 외부에 그대로 드러난다.
-- 실제 발급되는 내부 숙소 ID(surrogate PK)를 도입하고, 기존 복합키는 UNIQUE 제약으로
-- 유지해 "같은 (supplier, external_hotel_code)는 항상 같은 내부 식별자로 매핑된다"는
-- 요구사항을 계속 DB 레벨에서 보장한다. 근거: docs/architecture.md "매핑 테이블".
ALTER TABLE room_type_mapping
    DROP FOREIGN KEY fk_room_type_mapping_hotel;

ALTER TABLE hotel_mapping
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD CONSTRAINT uq_hotel_mapping UNIQUE (supplier, external_hotel_code);

ALTER TABLE room_type_mapping
    ADD CONSTRAINT fk_room_type_mapping_hotel
        FOREIGN KEY (supplier, external_hotel_code)
        REFERENCES hotel_mapping (supplier, external_hotel_code);
