# HMPPS Prisoner Finance SYNC API

[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-finance-sync-api)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/hmpps-prisoner-finance-sync-api "Link to report")
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prisoner-finance-sync-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-finance-sync-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

## Contents
- [About this project](#about-this-project)
- [Project set up](#project-set-up)
- [Running the application locally](#running-the-application-locally)
  - [Running the application in intellij](#running-the-application-in-intellij)
- [Architecture](#Architecture)

## About this project

An API used by the NOMIS application to sync with the Prisoner Finance Ledger.

It is built using [Spring Boot](https://spring.io/projects/spring-boot/) and [Kotlin](https://kotlinlang.org/) as well as the following technologies for its infrastructure:

- [AWS](https://aws.amazon.com/) - Services utilise AWS features through Cloud Platform.
- [Cloud Platform](https://user-guide.cloud-platform.service.justice.gov.uk/#cloud-platform-user-guide) - Ministry of Justice's (MOJ) cloud hosting platform built on top of AWS which offers numerous tools such as logging, monitoring and alerting for our services.
- [Docker](https://www.docker.com/) - The API is built into docker images which are deployed to our containers.
- [Kubernetes](https://kubernetes.io/docs/home/) - Creates 'pods' to host our environment. Manages auto-scaling, load balancing and networking to our application.

## Project set up

Enable pre-commit hooks for formatting and linting code with the following command;

```bash
./gradlew addKtlintFormatGitPreCommitHook addKtlintCheckGitPreCommitHook
```

## Running the application locally

The application comes with a `local` spring profile that includes default settings for running locally.

There is also a `docker-compose.yml` that can be used to run a local instance in docker and also an
instance of HMPPS Auth.

```bash
make serve
```

will build the application and run it and HMPPS Auth within a local docker instance.

To verify the app has started, 
1. ensure the containers are visible (and running) in Docker, and 
2. visit http://localhost:8080/health ensuring the result contains "status: UP"  

### Running the application in Intellij

```bash
make serve-environment
```

will just start a docker instance of HMPPS Auth with a PostgreSQL database. The application should then be started with 
a `local` active profile in Intellij.

```bash
make serve-clean-environment
```

will also reset the database

## Architecture

For details of the current proposed architecture [view our C4 documentation](./docs/architecture)


## Coverage
### Show coverage in intellij
- Build the project
- Go to Run in the main menu.
- Select Manage Coverage Reports
- Add the files in build/jacoco/
- Click OK. The coverage report will now appear in the Coverage Tool Window and the code will be highlighted in the editor.

### Open Coverage report in the browser
To visualize the reports in the browser:
- Build the project
- Open the `index.html` files in the folders under `build/reports/jacoco`


## API Documentation
Is available on a running local server at http://localhost:8080/swagger-ui/index.html#/

### Health
- `/health`: provides information about the application health and its dependencies. 
- `/info`: provides information about the version of deployed application.

## Using local API endpoints

### Generating an auth token
- Use this command to request a local auth token:
  ```bash
  curl -X POST "http://localhost:8090/auth/oauth/token?grant_type=client_credentials" -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n hmpps-prisoner-finance-sync-api-1:clientsecret | base64)"
  ```
  
- The response body will contain an access token something like this:

  ```json
  {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5...BAtWD653XpCzn8A",
    "token_type": "bearer",
    "expires_in": 3599,
    "scope": "read write",
    "sub": "hmpps-prisoner-finance-sync-api-1"        
  }
  ```
- Use the value of `access_token` as a Bearer Token to authenticate when calling the local API endpoints.