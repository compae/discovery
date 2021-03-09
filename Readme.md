# Discovery

In this repo we build the docker image that contains the stratio-modified metabase, along with our metabase drivers, our custom start-up scripts with secrets, our api to export and import metabase collections (discovery-cicd), and our nginx server to perform oauth with the Stratio SSO. We have a fork of metabase in a separete repo (https://github.com/Stratio/discovery) where we only put the changes to the metabase core. Just like in metabase, our docker build is done using a multi-stage build, where the "builder" image starts from the one we build in our metabase-forked repository. In that builder image, we pack the metabase souce code and build scripts, with a maven .m2 repository with the metabase-core dependency installed so that we can build the metabase drivers from here.

## Extra metabase features added by Stratio

### SSO integration and auto-login via headers

The integration with the Stratio SSO consists of two pieces. We have a nginx in front of metabase (and metabase only listens in localhost so the only way to access is through this nginx) that authenticates users via oauth with the SSO and sets in the request sent to the metabase backend two http headers with the user name and the groups. Metabase reads these headers (more info on the details on how it reads these headers below) and checks the received user groups against a whitelist set in the environment variable `ALLOWED_GROUPS` as a comma separated list of values. If the user is allowed to pass, the user and the groups are created in the metabase database if they do not exist, the user is assigned to these groups. Metabase also checks if the user belongs to a group tagged as administrators (set in the environment variable `ADMIN_GROUP`), and in case it does, the user is set as a metabase administrator. Everytime the users log in, this information (the groups they belong to and their admin status) is sync'd. The whitelist checking and the group syncing can be disabled.

We have implemented this in such a way that there is no need for the user to actively log in metabase by adding a middleware to the metabase server that sits between the metabase middleware that sets into the request the session id read from the cookie, and the one that adds the user information in the request based this session found in the request. Our custom middleware checks if there is a session in the request, and if there is no session it checks for the headers with the user info, creates the user, creates a session for the user and sets the session in the request (so the next middleware can associate the request with the user) and sets a cookie in the response with the session, so the user is "logged in". Note that we only do this if we have the SSO (or Datio headers authentication activated) and only for the `/api/user/current` request.

For the Datio case where our nginx does not make oauth and just pass through all requests, the auto-login behavior is the same, but in the Datio case, there are some Datio pieces that set the headers (it can also be a jwt token, that our custom middleware also knows how to read and verify).

There are a couple of things we can configure about this auto login behavior:

  - The "authenticator" to use (`AUTHENTICATOR`) which can be `gosec-sso`, `headers` or `jwt`. The standard one is `gosec-sso`, the last two are the ones to be used in Datio where the headers (or maybe a jwt) are set by an external proxy.
  - Whether to use a whitelist or not (`USE_GROUP_WHITELIST=true|false`). If not using a whitelist, anyone that can log in the sso proxy will have access to discovery.
  - The whitelist to use (`ALLOWED_GROUPS`) which is a string with the allowed group separated by commas
  - An admin group so that users belonging to that group will be metabase Administrators (`ADMIN_GROUP`) (We had also this in the old intetgration but the name `MB_ADMIN_GROUP_HEADER` was confusing so we have changed it)
  - Whether to create the received user groups as metabase groups and sync the user groups in metabase in each login (`CREATE_AND_SYNC_GROUPS=true|false`). Note that when the group whitelist is activated only the user groups belonging to the whitelist will be created. 
  - For the Datio case using headers, the name of the headers containing the username and the groups can be configured with the environment variables `MB_USER_HEADER` and `MB_GROUP_HEADER`. For the case of using a jwt the header containing the claim, the user and groups claim, and the endpoint to download the public key to validate the jwt can be configured with the environment variables `JWT_HEADER_NAME`, `JWT_USERNAME_CLAIM`, `JWT_GROUPS_CLAIM` and `JWT_PUBLIC_KEY_ENDPOINT`

When using this kind of _auto-login_ authenticators, we also disable the email login, and we do not allow the users to change their names (it would be insecure if the "impersonation" features of our custom drivers are activated). This is done again using custom middleware.

### Stratio Crossdata and Stratio Postgres Drivers

The source code for these drivers lives in this repository and they are built using the _stratio-metabase-builder_ docker image created in our metabase fork repository. However, to have a better experience developing the drivers we recommend copying them (or linking them) to the metabase fork repository and then follow the metabase readme instructions. Once the feature or fix is developed, copy them back here and make the pull request. 

The crossdata driver basically is a copy/paste of the sparksql driver (which inherits from the hive-like abstract driver). We have copy/pasted the code because in our build we do not include the sparksql nor the hive-like drivers due to them having stopper vulnerabilities, so we cannot inherit from the sparksql driver and just override the driver methods we need. We have done a small fix/change in the metabase core to handle cases when the crossdata jdbc sends back a resultSetMetadata with zero columns (for instance in `CREATE TABLE` queries).

The stratiopostgres driver is basically the same as the postgres driver but we provide the option to set a custom postgres variable set to the current logged user before every query. Our implementation just inherits from the postgres driver and just overrides the method that creates the jdbc connection and just sets the variable with the logged user.

### Audit logs

This is provided by the nginx running in the container via its access log. In order to be able to log the current user in the cases where the nginx does not authenticate users (Datio headers or using native metabase login), we have added more middleware to metabase so that we set an http header `Metabase-User` in all responses with the current user associated with the session. This header is read by the nginx and used to log the user in the access log.

### Remove vulnerabilities

In order to remove the vulnerabilities present in metabase we have to do the following:

  - Exclude the sqlite and sparksql drivers (we remove them before making the build) 
  - Upgrade `org.quartz-scheduler` (which is a transitive dependency of clojurewerkz/quartzite). This in turn forces us to include an extra change in the metabase core in the `task.clj` file where they implement an interface that now needs to implement an extra method. 
  - Upgrade the dependency `com.taoensso/nippy`
  - Delete the dependency `metabase/saml20-clj` which brings the vulnerable transitive dependency `cryptacular`. We can safely delete this dependency since it is only needed for the enterprise edition build.
