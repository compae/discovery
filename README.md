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



* Running jar:

    ```bash
      $ java -jar /app/target/uberjar/discovery-cicd.jar export --file collection.json --user <<user>> --pass <<password>> --collection-id <<collection_id>>
      $ java -jar /app/target/uberjar/discovery-cicd.jar import --file collection.json --user <<user>> --pass <<password>>    
    ```
    
where:
*collection_id* is the collection to export or import  