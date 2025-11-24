workspace "HMPPS Prisoner Finance (PF)" "All services, systems and components that make up The HMPPS Prisoner Finance Service (PF)" {

    !identifiers hierarchical
    !impliedRelationships true

    model {
        properties {
            "structurizr.groupSeparator" "::"
        }

        !include ./domain/archetypes.dsl

        group "Users" {
            group "Prison Users" {
                prison = person "Prison staff"
                cashier = person "Prison cashier"
                prisoner = person "Prisoner"
            }

            group "HMPPS Users" {
                FBP = person "Finance business partner"
                accountant = person "Accountant"
            }

            group "External Users" {
                FandF = person "Friends and family"
            }
        }

        group "Digital Prison Services (DPS)" {
            hmpps-auth = externalSystem "HMPPS Auth service"
            hmpps-audit = externalSystem "HMPPS Audit service"
            prisonerProfile = externalSystem "Prisoner profile service"
            prisonerSearch = externalSystem "Prisoner search service"
            activities = externalSystem "Activities service"
            adjudications = externalSystem "Adjudications service"
            integrationAPI = externalSystem "External integration API"
        }

        group "Prison services" {
            launchpad = externalSystem "Launchpad"
            SMTP = externalSystem "Send money to prisoners service"

            domainEvents = externalSystem "Prison domain events" {
                tags "Amazon Web Services - Simple Queue Service SQS Queue"
            }
        }

        PF = softwareSystem "Prisoner finance service" {
          generic = expressFrontend "Prisoner Finance UIs"
          specialised = expressFrontend "Specialised task UIs"

          payments = springBootAPI "Payments" {
            !docs ./docs/purchasing-processes.md

            credit = component "Credit endpoint"
            debit = component "Debit endpoint"
          }

          generalLedger = springBootAPI "General ledger" {
            accounts = component "Accounts endpoint"
            transactions = component "Transactions endpoint"
          }

          datastore = datastore "General ledger DB"

          sync = springBootAPI "Sync service" "A service to allow NOMIS to sync with Prisoner Finance"  {
            !docs ./docs/sync-processes.md

            syncPrisonerTransactions = component "Sync offender transactions"
            migratePrisonerBalances = component "Migrate prisoner balances"
            reconcilePrisonerBalances = component "Reconcile prisoner balances"

            syncGeneralLedgerTransactions = component "Sync general ledger transactions"
            migrateGeneralLedgerBalances = component "Migrate general ledger balances"
            reconcileGeneralLedgerBalances = component "Reconcile general ledger balances"
          }

          payments.credit --https-> hmpps-auth "Reads from"
          payments.credit --https-> hmpps-audit "Writes to"
          payments.credit --https-> datastore "Writes to"

          payments.debit --https-> hmpps-auth "Reads from"
          payments.debit --https-> hmpps-audit "Writes to"
          payments.debit --https-> datastore "Writes to"

          generalLedger.accounts --https-> hmpps-auth "Reads from"
          generalLedger.accounts --https-> hmpps-audit "Writes to"
          generalLedger.accounts --https-> datastore "Reads from"
          generalLedger.accounts --https-> prisonerSearch "Searches"

          generalLedger.transactions --https-> hmpps-auth "Reads from"
          generalLedger.transactions --https-> hmpps-audit "Writes to"
          generalLedger.transactions --https-> datastore "Reads from"
          generalLedger.transactions --https-> prisonerSearch "Searches"

          generic --https-> hmpps-auth "Reads from"
          generic --https-> hmpps-audit "Writes to"
          generic --https-> generalLedger.accounts "Reads from"
          generic --https-> generalLedger.transactions "Reads from"
          generic --https-> payments.debit "Writes to"
          generic --https-> payments.credit "Writes to"

          specialised --https-> hmpps-auth "Reads from"
          specialised --https-> hmpps-audit "Writes to"
          specialised --https-> generalLedger.accounts "Reads from"
          specialised --https-> payments.debit "Writes to"
          specialised --https-> payments.credit "Writes to"

          sync.syncPrisonerTransactions --https-> datastore "Writes to"
          sync.migratePrisonerBalances --https-> datastore "Writes to"
          sync.reconcilePrisonerBalances --https-> datastore "Reads from"

          sync.syncGeneralLedgerTransactions --https-> datastore "Writes to"
          sync.migrateGeneralLedgerBalances --https-> datastore "Writes to"
          sync.reconcileGeneralLedgerBalances --https-> datastore "Reads from"
        }

        group "External vendors" {
            BT = externalSystem "BT PIN phone service"
            DHL = externalSystem "DHL canteen service"
            unilink = externalSystem "Unilink Custodial Management System"
        }

        group "Legacy systems" {
            NOMIS = legacySystem "NOMIS" "Prison Management System" {
                application = container "NOMIS Application" {
                    technology "Oracle Forms"
                }

                nomis-db = datastore "NOMIS Database" "Data store for prison management. Includes a full history and associated information related to the management of people in prison" {
                    technology "Oracle Database"
                }
            }

            prison-api = legacySystem "Prison API"

            NOMIS.application -> NOMIS.nomis-db "Reads from"
            NOMIS.application -> NOMIS.nomis-db "Writes to"
            prison-api -> NOMIS.nomis-db "Reads from"
            prison-api -> NOMIS.nomis-db "Writes to"
        }

        group "Cabinet office" {
            SOP = externalSystem "Single Operating Platform (SOP)"
        }

        group "Government Digital Service (GDS)" {
            govPay = externalSystem "GOV UK Pay"
        }

        group "Bank accounts" {
            hmppsGeneral = externalSystem "HMPPS general"
            prisonerTrustFunds = externalSystem "Prisoner trust funds"
        }

        prison --https-> PF.generic "Uses"
        prison --https-> PF.specialised "Uses"
        prison --https-> prisonerProfile "Uses"
        prison --https-> prisonerSearch "Uses"
        prison --https-> activities "Uses"
        prison --https-> SMTP "Uses"
        prison --https-> SOP "Uses"
        prison --https-> NOMIS.application "Uses"
        prison --https-> unilink "Uses"

        cashier --https-> SOP "Uses"
        cashier --https-> PF.generic "Uses"
        cashier --https-> PF.specialised "Uses"

        prisoner --https-> launchpad "Uses"
        prisoner --https-> unilink "Uses"

        FBP --https-> SOP "Uses"

        accountant --https-> SOP "Uses"

        FandF --https-> SMTP "Uses"
        FandF --https-> govPay "Uses"

        launchpad --https-> PF.generalLedger.accounts "Reads from"
        launchpad --https-> PF.payments.debit "Writes to"
        launchpad --https-> PF.generalLedger.transactions "Reads from"

        activities --https-> PF.payments.credit "Writes to"

        adjudications --https-> PF.payments.debit "Writes to"

        SMTP --https-> prison-api "Writes to"
        SMTP --https-> prison-api "Reads from"
        SMTP --https-> PF.payments.credit "Writes to"
        SMTP --https-> PF.payments.debit "Writes to"
        SMTP --https-> PF.generalLedger.accounts "Reads from"

        integrationAPI --https-> PF.generalLedger.accounts "Reads from"
        integrationAPI --https-> PF.generalLedger.transactions "Reads from"
        integrationAPI --https-> PF.payments.credit "Writes to"
        integrationAPI --https-> PF.payments.debit "Writes to"

        prisonerProfile --https-> PF.generalLedger.accounts "Reads from"
        prisonerProfile --https-> PF.generalLedger.transactions "Reads from"

        PF -> hmppsGeneral "Instructs"
        PF -> prisonerTrustFunds "Instructs"
        PF -> SOP "Instructs"

        GOVPay -> prisonerTrustFunds "Moves money into"

        SOP -> hmppsGeneral "Instructs"
        SOP -> prisonerTrustFunds "Instructs"

        BT --https-> integrationAPI "Writes to"
        BT --https-> integrationAPI "Reads from"

        DHL --https-> integrationAPI "Writes to"
        DHL --https-> integrationAPI "Reads from"

        unilink --https-> integrationAPI "Writes to"
        unilink --https-> integrationAPI "Reads from"

        hmpps-auth --https-> NOMIS.nomis-db "Reads from"

        # Sync with NOMIS
        NOMIS.application --json-> PF.sync "Writes to"
        NOMIS.application --json-> domainEvents "Listens to"

        NOMIS.application --json-> PF.sync.syncPrisonerTransactions "Writes to"
        NOMIS.application --json-> PF.sync.migratePrisonerBalances "Writes to"
        NOMIS.application --json-> PF.sync.reconcilePrisonerBalances "Reads from"

        NOMIS.application --json-> PF.sync.syncGeneralLedgerTransactions "Writes to"
        NOMIS.application --json-> PF.sync.migrateGeneralLedgerBalances "Writes to"
        NOMIS.application --json-> PF.sync.reconcileGeneralLedgerBalances "Reads from"

        PF.payments.debit --json-> domainEvents "Publishes to"
        PF.payments.credit --json-> domainEvents "Publishes to"

        !include ./domain/environments/dev.dsl
    }

    !adrs ./adrs

    views {
        !include ./domain/views.dsl

        theme default
        theme https://static.structurizr.com/themes/amazon-web-services-2020.04.30/theme.json
        theme https://static.structurizr.com/themes/amazon-web-services-2022.04.30/theme.json
        theme https://static.structurizr.com/themes/amazon-web-services-2023.01.31/theme.json
        theme ./domain/theme.json
    }
}
