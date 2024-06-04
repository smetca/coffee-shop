CREATE
DATABASE coffeeshop;
\c coffeeshop;

CREATE TABLE tickets
(
    id             uuid             DEFAULT gen_random_uuid(),
    time           bigint  NOT NULL,
    serverId       uuid    NOT NULL,
    tableNumber    integer NOT NULL,
    guestName      text    NOT NULL,
    numberOfGuests integer NOT NULL,
    orders         text[],
    completed      boolean NOT NULL DEFAULT false,
);

ALTER TABLE tickets
    ADD CONSTRAINT pk_tickets PRIMARY KEY (id);

CREATE TABLE users
(
    email          text NOT NULL,
    hashedPassword text NOT NULL,
    firstName      text,
    lastName       text,
    role           text NOT NULL
);

ALTER TABLE users
    ADD CONSTRAINT pk_users PRIMARY KEY (email);