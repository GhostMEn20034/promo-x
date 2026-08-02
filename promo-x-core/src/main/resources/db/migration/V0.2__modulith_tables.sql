CREATE TABLE event_publication
(
    id                     UUID                        NOT NULL,
    publication_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    listener_id            VARCHAR                     NOT NULL,
    serialized_event       VARCHAR                     NOT NULL,
    event_type             VARCHAR                     NOT NULL,
    completion_date        TIMESTAMP WITHOUT TIME ZONE,
    last_resubmission_date TIMESTAMP WITHOUT TIME ZONE,
    completion_attempts    INTEGER                     NOT NULL,
    status                 VARCHAR(255),
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE TABLE event_publication_archive
(
    id                     UUID                        NOT NULL,
    publication_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    listener_id            VARCHAR                     NOT NULL,
    serialized_event       VARCHAR                     NOT NULL,
    event_type             VARCHAR                     NOT NULL,
    completion_date        TIMESTAMP WITHOUT TIME ZONE,
    last_resubmission_date TIMESTAMP WITHOUT TIME ZONE,
    completion_attempts    INTEGER                     NOT NULL,
    status                 VARCHAR(255),
    CONSTRAINT pk_event_publication_archive PRIMARY KEY (id)
);