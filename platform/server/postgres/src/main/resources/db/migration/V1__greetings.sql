CREATE TABLE greetings (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    text       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
