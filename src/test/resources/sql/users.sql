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

INSERT INTO users (
    email,
    hashedPassword,
    firstName,
    lastName,
    role
) VALUES (
    'sam@coffeeshop.com',
    '$2a$10$QtmB49k67mjHdESLTjqYIeE3.h2BwmInrUTwx9Roz8L/v58v21v9W',
    'Sam',
    'Metalfe',
    'ADMIN'
);

INSERT INTO users (
    email,
    hashedPassword,
    firstName,
    lastName,
    role
) VALUES (
    'john@coffeeshop.com',
    '$2a$10$a1npC/0GTqevldGQECrrj.FLwSZvZP96cwkcYTMsGe2J2TSkHwVTS',
    'John',
    'Shaw',
    'SERVER'
);