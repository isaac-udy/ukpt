CREATE OR REPLACE FUNCTION notify_greetings_greeting_changed() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('greeting_changed', COALESCE(NEW.id, OLD.id)::text);
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS greetings_greeting_changed_notify ON greetings;
CREATE TRIGGER greetings_greeting_changed_notify
AFTER INSERT OR UPDATE OR DELETE ON greetings
FOR EACH ROW EXECUTE FUNCTION notify_greetings_greeting_changed();
