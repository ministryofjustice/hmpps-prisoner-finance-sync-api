
archetypes {
    team = person {
        tags "team"
    }

    application = container {
        tags "Application"
    }

    api = application {
        technology "Kotlin"
        tags "API"
    }

    algorithm = application {
        technology "Python"
        tags "Algorithm"
    }

    springBootAPI = api {
        technology "Spring Boot"
    }

    frontend = application {
        technology "Typescript"
        tags "UI"
    }

    expressFrontend = frontend {
        technology "ExpressJS"
    }

    datastore = container {
        tags "Datastore"
    }

    rds = datastore {
        tags "Amazon Web Services - RDS PostgreSQL instance"
        technology "PostgreSQL"
    }

    blobstore = datastore {
        tags "Amazon Web Services - Simple Storage Service S3"
        technology "S3"
    }

    queue = container {
        tags "Queue"
    }

    sqsQueue = container {
        tags "Amazon Web Services - Simple Queue Service SQS Queue"
        technology "SQS"
    }

    snsTopic = container {
        tags "Amazon Web Services - Simple Notification Service SNS Topic"
        technology "SNS"
    }

    externalSystem = softwareSystem {
        tags "External System"
    }

    externalApplication = application {
        tags "External Application"
    }

    externalApi = externalApplication {
        tags "External API"
    }

    managedService = externalSystem {
        tags "Managed Service"
    }

    legacySystem = softwareSystem {
        tags "Legacy System"
    }

    sync = -> {
        tags "Synchronous"
    }

    async = -> {
        tags "Asynchronous"
    }

    email = --async-> {
        technology "SMTP"
    }

    https = --sync-> {
        technology "HTTPS"
    }

    json = --https-> {
        technology "JSON/TLS"
    }
}