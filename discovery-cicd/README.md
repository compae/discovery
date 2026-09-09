# discovery-cicd

## Overview

This project contains a easy-to-use export/import tool of any Discovery's collection. 
This collections may contain both dashboards and questions, even others nested sub-collections which will be exported/imported as well.

## Usages

This tool can be used both via curl and running the jar-library 

* Curl commands:
    
    ```bash
      $ curl '<<instance_from_discovery>>/discovery-cicd/export/<<collection_id>>'  -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --insecure >> collection.json
      $ curl '<<instance_to_discovery>>/discovery-cicd/import' -H 'Content-Type: application/json' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' -d @coleccion.json --insecure
    ```
where:

*instance_from_discovery* and *instance_to_discovery* are the exposed URLs in the source and sink discovery

*collection_id* is the collection to export or import

*metabase.SESSION-cookie* is the content of the cookie called 'metabase.SESSION' 



* Asynchronous curl commands:

  An export of a collection with thousands of questions takes tens of minutes and the synchronous
  endpoint sends nothing at all until it is over, so no connection through the ingress survives it.
  These endpoints answer with a job id straight away and the work carries on in the background:

    ```bash
      # start it: answers 202 with {"jobId": ..., "statusUrl": ..., "resultUrl": ...}
      $ curl -XPOST '<<instance_from_discovery>>/discovery-cicd/async/export/<<collection_id>>' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --insecure

      # ask how it is going, as often as you like
      $ curl '<<instance_from_discovery>>/discovery-cicd/async/jobs/<<job_id>>' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --insecure

      # once it says SUCCEEDED, download it (it is gzipped, --compressed unpacks it)
      $ curl --compressed '<<instance_from_discovery>>/discovery-cicd/async/jobs/<<job_id>>/result' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --insecure > collection.json

      # import it in the target instance, the same way ('--data-binary' and not '-d': '-d' strips
      # the newlines of the file)
      $ curl -XPOST '<<instance_to_discovery>>/discovery-cicd/async/import' -H 'Content-Type: application/json' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --data-binary @collection.json --insecure

      # or download it packed and upload it as it is, which is what you want with a big collection:
      # the measured export is 203MB and 5.3MB gzipped. 'Accept-Encoding' is what keeps it packed,
      # and without it the file comes unpacked whatever you name it; 'Content-Encoding' is what says
      # it is packed on the way back, and without it the file is taken for the json itself
      $ curl '<<instance_from_discovery>>/discovery-cicd/async/jobs/<<job_id>>/result' -H 'Accept-Encoding: gzip' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --insecure > collection.json.gz
      $ curl -XPOST '<<instance_to_discovery>>/discovery-cicd/async/import' -H 'Content-Type: application/json' -H 'Content-Encoding: gzip' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --data-binary @collection.json.gz --insecure

      # and stop one you no longer want (this is also how you delete a finished result)
      $ curl -XDELETE '<<instance_from_discovery>>/discovery-cicd/async/jobs/<<job_id>>' -H 'x-metabase-session: <<metabase.SESSION-cookie>>' --insecure
    ```

  `GET /discovery-cicd/async/jobs` lists them all, and takes `?status=` and `?type=` to filter.

  The status answers how far it has got, phase by phase. The number of questions and of dashboards is
  known from the very first answer, because listing the items of the collection costs one request and
  already says how many there are; the tables and the fields say `"total": null` until the questions
  have been read, which is when they are known:

    ```json
      { "jobId": "8f2c...", "type": "EXPORT", "status": "RUNNING",
        "percentage": 47, "currentPhase": "questions",
        "message": "questions: 28100 of 60000",
        "elapsedSeconds": 412, "estimatedSecondsRemaining": 890,
        "phases": [ { "phase": "questions", "status": "RUNNING", "done": 28100, "total": 60000 },
                    { "phase": "tables",    "status": "PENDING", "done": 0,     "total": null  } ] }
    ```

  An import never holds the export it was given: the file is read once to learn what it holds and
  then once more per pass, and only one question, one dashboard or one subscription is in memory at
  a time. Measured on the export of a collection with 60.000 questions (203MB): reading it whole
  used to keep 1169MB of heap and take 173s, against 146MB and 3s now, which is what makes it fit in
  the ~1GB the pod gives this process (`-XX:MaxRAMPercentage=25.0`, with metabase taking 70% of the
  same pod).

  **Jobs and their results live in this process only.** If the pod, or the discovery-cicd process
  alone, is restarted, they are gone and the status endpoint answers 404. A finished job is dropped,
  and its result deleted, after `DISCOVERY_CICD_JOBS_RETENTION` (24h by default) or once there are
  more than `DISCOVERY_CICD_JOBS_MAX_STORED_RESULTS` of them: the filesystem of the pod is ephemeral
  and an export of a big collection is hundreds of megabytes.

  If the service is deployed with more than one instance, the request asking about a job has to reach
  the same pod that is running it. Keep `INSTANCES` at 1 while promoting collections.

* Running jar:

    ```bash
      $ java -jar /app/target/uberjar/discovery-cicd.jar export --file collection.json --user <<user>> --pass <<password>> --collection-id <<collection_id>>
      $ java -jar /app/target/uberjar/discovery-cicd.jar import --file collection.json --user <<user>> --pass <<password>>    
    ```
    
where:
*collection_id* is the collection to export or import  