CREATE TABLE tickets
(
    id             uuid             DEFAULT gen_random_uuid(),
    time           bigint  NOT NULL,
    serverId       uuid    NOT NULL,
    tableNumber    integer NOT NULL,
    guestName      text    NOT NULL,
    numberOfGuests integer NOT NULL,
    orders         text[], -- Just a simple representation for demonstration purposes
    completed      boolean NOT NULL DEFAULT false
);

ALTER TABLE tickets
    ADD CONSTRAINT pk_tickets PRIMARY KEY (id);

INSERT INTO tickets(
                    id,
                    time,
                    serverId,
                    tableNumber,
                    guestName,
                    numberOfGuests,
                    orders,
                    completed
                    )
VALUES ('243df418-ec6e-4d49-9279-f799c0f40064', -- id
        1659186086, -- time
        '443df418-cd4e-9f33-9279-f799c0f40064', -- serverId
        12, -- tableNumber
        'Alexa', -- guestName
        2, -- numberOfGuests
        ARRAY [ 'late', 'cappuccino', 'cookie' ], -- orders
        false -- completed
       );