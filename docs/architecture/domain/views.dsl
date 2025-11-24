systemLandscape "prisoner-finance-system-landscape" "HMPPS Prisoner finance" {
    include *

    autolayout tb 150 150
}

systemContext PF "prisoner-finance-system-context" {
    include *

    autolayout tb 150 150
}

systemContext NOMIS "nomis-system-context" {
    include *

    autolayout lr 150 150
}

container PF "prisoner-finance-container-view" {
    include *

    autolayout tb 150 150
}

component PF.payments "payments-component-view" {
  include *

  autolayout tb 150 150
}

component PF.generalLedger "general-ledger-component-view" {
  include *

  autolayout tb 150 150
}

component PF.sync "sync-component-view" {
  include *

  autolayout tb 150 150
}

deployment PF "Dev" "prisoner-finance-deployment-dev" {
    include *

    autolayout lr 150 150
}