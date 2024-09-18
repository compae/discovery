

-- v0.43.4-0.1.0-002
-- Create trigger ensuring newly created subscriptions have unique names.
-- If it is a pulse (dashboard_id = null) or the dashboard subscription name
-- is different from the dashboard name keep the same name
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


-- v0.43.4-0.1.0-003
-- Create trigger ensuring subscription names do not change during updates
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


 CREATE TRIGGER stratio_update_subscriptions
 BEFORE UPDATE ON pulse
 FOR EACH ROW
 EXECUTE PROCEDURE stratio_update_subscription();
