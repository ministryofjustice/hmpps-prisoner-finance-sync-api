workspace "HMPPS Electronic Monitoring" "This theme includes element styles with icons for each of the Electronic Monitoring services, it includes the AWS Architecture Icons (https://aws.amazon.com/architecture/icons/)." {

    views {
        styles {
            element "Element" {
                shape roundedbox
            }

            element "Person" {
                shape person
                background #08427b
                color #ffffff
            }

            element "Team" {
                shape person
                background #1168bd
                color #ffffff
            }

            element "Application" {
                background #1168bd
                color #ffffff
            }

            element "Software System" {
                background #1168bd
                color #ffffff
            }

            // legacy systems

            element "Legacy System" {
                background #cccccc
                color #000000
            }

            // external systems

            element "External System" {
                background #3598EE
                color #000000
            }

            element "External API" {
                background #84a6b8
                color #000000
            }

            // datastores

            element "Datastore" {
                shape cylinder
                background #84a6b8
            }

            // message queues

            element "Amazon Web Services - Simple Queue Service SQS Queue" {
                shape pipe
            }

            element "Amazon Web Services - Simple Notification Service SNS Topic" {
                shape pipe
            }

            element "Queue" {
                shape pipe
                background #84a6b8
                color #000000
            }

            // lambda

            element "Lambda" {
                shape circle
                background #FFAC33
            }
        }
    }
}