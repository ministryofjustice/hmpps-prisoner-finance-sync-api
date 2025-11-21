dev = deploymentEnvironment "DEV" {

    MOJ = deploymentNode "MoJ Cloud Platform" {
        tags "Amazon Web Services - Cloud"

        cloud = deploymentNode "EU-West-2 (London)" {
            tags "Amazon Web Services - Region"

            route53 = infrastructureNode "Route 53" {
                tags "Amazon Web Services - Route 53"
            }
            ELB = infrastructureNode "Elastic Load Balancer" {
                tags "Amazon Web Services - Elastic Load Balancing Application Load Balancer"
            }
            route53 --https-> ELB "Forwards requests to"

            k8 = deploymentNode "Kubernetes" {
                tags "Amazon Web Services - Elastic Kubernetes Service"

                genericWebApplicationInstance = containerInstance PF.generic
                specialisedWebApplicationInstance = containerInstance PF.specialised
                apiApplicationInstance = containerInstance PF.generalLedger
                syncApplicationInstance = containerInstance PF.sync
            }

            elb --https-> k8.genericWebApplicationInstance "Forwards requests to"
            elb --https-> k8.specialisedWebApplicationInstance "Forwards requests to"

            k8.syncApplicationInstance --https-> domainEvents "Publishes to"
            k8.syncApplicationInstance --https-> domainEvents "Listens to"

            rds = deploymentNode "Amazon RDS" {
                tags "Amazon Web Services - RDS"

                generalLedger = containerInstance PF.datastore
            }
        }
    }
}
