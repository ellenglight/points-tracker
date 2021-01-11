# points-tracker

## Overview

points-tracker is a web server written in Scala using the [Play Framework](https://www.playframework.com/). It implements an in-memory data store using [Akka actors](https://doc.akka.io/docs/akka/current/actors.html)
and processes requests to add new users, access user data, and modify user accounts.

* application code: https://github.com/ellenglight/points-tracker/tree/main/points-tracker/app
* tests: https://github.com/ellenglight/points-tracker/tree/main/points-tracker/test

## Running the application locally

### Install dependencies
1. Install Java8: https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=hotspot
     * To verify on Mac OS X: run `java -version` and confirm the version is `1.8`
2. Install [sbt](https://www.scala-sbt.org/download.html) version `1.3.13`. Can be installed in many ways, this is what I would recommend for Mac OS X users: 
    * Install SDKMAN (Homebrew will not use Java 8 as required): https://sdkman.io/install
    * `sdk install sbt 1.3.13`
    * Verify: `sbt sbtVersion`

### Run the server
1. Navigate to folder: `cd points-tracker/points-tracker`
1. Start the server: `sbt run`. The server will respond at `http://localhost:9000/`
1. Sample requests:
<details>
  <summary>Click to expand</summary>
  
  #### Add a new user
  ```
  curl --location --request POST 'http://localhost:9000/add/new/user'
  ```
  Note the user id returned in the response body (referred to as `<userid>` in the following requests)
  #### Add some points for the user
  ```
  curl --location --request POST 'http://localhost:9000/add/points/<userid>' \
--header 'Content-Type: application/json' \
--data-raw '{
    "userId": "<userid>",
    "value": 1000,
    "company": "DANNON"
}'
  ```
  * Note: the possible values for `"company"` are `"DANNON"`, `"UNILEVER"`, and `"MILLERCOORS"`.
  * Note: to add negative points for a company, just pass a negative integer for `"value"`.
  #### Deduct points for the user
  ```
  curl --location --request POST 'http://localhost:9000/deduct/points/<userid>' \
--header 'Content-Type: application/json' \
--data-raw '{
    "userId":"<userid>",
    "value": 500
}'
```
#### Get the total points for the user
```
curl --location --request GET 'http://localhost:9000/total/points/<userid>'
```
  
</details>

### Running the tests

1. Navigate to the code folder: `cd points-tracker/points-tracker`
1. Run the tests: `sbt test`

## Future Plans

1. Add logging
1. Add authentication
1. Add API documentation with swagger
