CREATE TABLE hotel_mapping (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier             VARCHAR(20)  NOT NULL,
    external_hotel_code  VARCHAR(100) NOT NULL,
    hotel_name           VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_hotel_mapping_supplier_code
        UNIQUE (supplier, external_hotel_code)
);

CREATE TABLE room_type_mapping (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_mapping_id         BIGINT NOT NULL,
    external_room_type_code  VARCHAR(100) NOT NULL,
    room_type_name           VARCHAR(255) NOT NULL,
    max_occupancy            INT NOT NULL,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_type_mapping_hotel
        FOREIGN KEY (hotel_mapping_id) REFERENCES hotel_mapping(id),
    CONSTRAINT uq_room_type_mapping
        UNIQUE (hotel_mapping_id, external_room_type_code)
);
