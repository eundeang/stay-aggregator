CREATE TABLE hotel_mapping (
    supplier             VARCHAR(20)  NOT NULL,
    external_hotel_code  VARCHAR(100) NOT NULL,
    hotel_name           VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (supplier, external_hotel_code)
);

CREATE TABLE room_type_mapping (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier                 VARCHAR(20)  NOT NULL,
    external_hotel_code      VARCHAR(100) NOT NULL,
    external_room_type_code  VARCHAR(100) NOT NULL,
    room_type_name           VARCHAR(255) NOT NULL,
    max_occupancy            INT NOT NULL,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_type_mapping_hotel
        FOREIGN KEY (supplier, external_hotel_code)
        REFERENCES hotel_mapping(supplier, external_hotel_code),
    CONSTRAINT uq_room_type_mapping
        UNIQUE (supplier, external_hotel_code, external_room_type_code)
);
