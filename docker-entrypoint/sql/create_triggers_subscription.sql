-- Pulses have their dashboard_id as null, we should allow updating pulses' names
CREATE FUNCTION stratio_update_subscription()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (NEW.dashboard_id IS NULL) THEN
        RETURN NEW;
    ELSE
        NEW.name := OLD.name;
        RETURN NEW;
    END IF;
END;
$$;

CREATE TRIGGER stratio_update_subscription
BEFORE UPDATE ON pulse
FOR EACH ROW
EXECUTE PROCEDURE stratio_update_subscription();


-- If it is a pulse (dashboard_id = null) or the dashboard subscription name is different from the dashboard name keep the same name
CREATE FUNCTION stratio_insert_subscription()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
	IF NEW.dashboard_id is not null AND NEW.name = (SELECT name FROM report_dashboard WHERE id = NEW.dashboard_id) THEN
	    NEW.name := uuid_in(md5(random()::text || random()::text)::cstring);
      	RETURN NEW;
    ELSE
      	RETURN NEW;
    END IF;
END;
$$;

CREATE TRIGGER stratio_insert_subscriptions
BEFORE INSERT ON pulse
FOR EACH ROW
EXECUTE PROCEDURE stratio_insert_subscription();


ALTER FUNCTION stratio_update_subscription()
  OWNER TO discovery;

ALTER FUNCTION stratio_insert_subscription()
  OWNER TO discovery;