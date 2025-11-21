# Sync from NOMIS

The sync service will need to be able to write batches of general ledger transactions to the service and batches of 
offender transactions. This will happen everytime a new batch of transactions is written to the NOMIS database via 
table events.

The sync service will also need to be able to write balances for each general ledger account, and offender subaccount, to 
the service with a timestamp and transaction id so that the service knows when this balance was correct and which 
transactions are included in the calculated balance. 

## Migrate Balance

```mermaid
sequenceDiagram
    actor SYSCON as Syscon developer
    box NOMIS
        participant NOMIS.DB as Database
        participant NOMIS.sync as Sync service
    end
    box Prisoner Finance
        participant PF.sync as Sync service
        participant PF.log as Sync log
        participant PF.ledger as General Ledger
        participant PF.calculator as Balance calculator
    end

    autonumber

    SYSCON ->> NOMIS.sync: Trigger balance update
    NOMIS.sync ->>+ NOMIS.DB: SELECT <Balance>
    NOMIS.DB -->>- NOMIS.sync: Return <Balance>
    NOMIS.sync ->>+ PF.sync: POST <Balance>
    PF.sync ->>+ PF.log: Create <Balance> log
    PF.log -->>- PF.sync: Success response
    PF.sync ->>+ PF.ledger: POST <Transaction>
    PF.ledger -->>- PF.sync: Success response
    PF.sync -->> NOMIS.sync: Success response
```

### Verify current balance

```mermaid
sequenceDiagram
    actor SYSCON as Syscon developer
    box NOMIS
        participant NOMIS.DB as Database
        participant NOMIS.sync as Sync service
    end
    box Prisoner Finance
        participant PF.sync as Sync service
        participant PF.log as Sync log
        participant PF.ledger as General Ledger
        participant PF.calculator as Balance calculator
    end

    autonumber

    SYSCON ->> NOMIS.sync: Trigger balance update
    NOMIS.sync ->>+ PF.sync: GET <Balance>
    PF.sync ->>+ PF.calculator: GET <Balance>
    PF.sync ->>+ PF.calculator: GET <Balance>
    PF.calculator ->>+ PF.log: GET <Balance>
    PF.log -->>- PF.calculator: Return <Balance>
    PF.calculator ->>+ PF.ledger: GET <GLTransaction>
    PF.ledger -->>- PF.calculator: Return <>
    PF.calculator -->> PF.calculator: Calculate
    PF.calculator -->>- PF.sync: Return <Balance>
    PF.sync -->>- NOMIS.sync: Return <Balance>
    NOMIS.sync ->>+ NOMIS.DB: SELECT <Balance>
    NOMIS.DB -->>- NOMIS.sync: Return <Balance>
    NOMIS.sync ->> NOMIS.sync: Compare
    alt When balances match
        NOMIS.sync -->> SYSCON: Success response
    else When balances fail to match
        NOMIS.sync -->> SYSCON: Failure response
    end
```

## Sync Offender Transaction

When a transaction involves either a credit to a prisoners subaccount or a debit from the prisoners subaccount, both an
`OffenderTransaction` record and two `GLTransaction` records are created in one database transaction.

The `OffenderTransaction` records is created to show the movement of money into or out of the prisoner's subaccount.

Two `GLTransaction` records are created to show the movement of money between the prisons overall budget for the collective prisoner 
subaccounts and the General Ledger account configured for the transaction type.

**NOTE:** *When an `OffenderTransaction` record is created to transfer money between two prisoner subaccounts, two mirrored 
`OffenderTransaction` records are created to show the money moving out of one subaccount and into the other subaccount 
along with the usual two `GLTransaction` records, but they will only be associated with the first `OffenderTransaction` 
the second will have no `GLTransaction` records.*

```mermaid
sequenceDiagram
    actor user as NOMIS User
    box NOMIS
        participant NOMIS.UI as UI
        participant NOMIS.DB as Database
        participant NOMIS.sync as Sync service
    end
    box Prisoner Finance
        participant PF.sync as Sync service
        participant PF.log as Sync log
        participant PF.ledger as General Ledger
    end

    autonumber

    user ->>+ NOMIS.UI: Record offender transaction
    NOMIS.UI ->>+ NOMIS.DB: INSERT <OffenderTransaction>
    NOMIS.UI ->> NOMIS.DB: INSERT <GLTransaction>
    NOMIS.DB ->> NOMIS.DB: Trigger <OffenderTransactionEvent>
    NOMIS.DB -->>- NOMIS.UI: Success response
    NOMIS.UI -->>- user: Success response
    NOMIS.sync ->>+ NOMIS.DB: SELECT <OffenderTransactionEvent>
    activate NOMIS.sync
    NOMIS.DB -->>- NOMIS.sync: Success response
    NOMIS.sync ->>+ NOMIS.DB: SELECT <OffenderTransaction>
    NOMIS.DB -->>- NOMIS.sync: Success response
    NOMIS.sync ->>+ PF.sync: POST <OffenderTransaction>
    PF.sync ->>+ PF.log: Create <Transaction> log
    PF.log -->>- PF.sync: Success response
    PF.sync ->>+ PF.ledger: Create <Transaction>
    PF.ledger -->>- PF.sync: Success response
    PF.sync -->>- NOMIS.sync: Success response
    deactivate NOMIS.sync
```

### Verify Offender transaction

```mermaid
sequenceDiagram
    box NOMIS
        participant NOMIS.sync as Sync service
        participant NOMIS.log as Sync log
        participant NOMIS.DB as Database
    end
    box Prisoner Finance
        participant PF.sync as Sync service
        participant PF.log as Sync log
        participant PF.ledger as General Ledger
    end

    autonumber

    NOMIS.sync ->>+ PF.sync: GET <OffenderTransaction>
    PF.sync ->> PF.ledger: GET <Transaction>
    PF.ledger -->> PF.sync: Return <Transaction>
    PF.sync -->>- NOMIS.sync: Return <OffenderTransaction>
    NOMIS.sync ->>+ NOMIS.DB: SELECT <OffenderTransaction>
    NOMIS.DB -->>- NOMIS.sync: Return <OffenderTransaction>
    NOMIS.sync ->> NOMIS.sync: Compare
    opt When OffenderTransactions fail to match
        NOMIS.sync ->> NOMIS.log: Create <SyncError> log
    end
```

Following this the NOMIS sync service will verify the balances.

## Sync General Ledger Transaction

When a transaction involves a transfer between two prison General Ledger account, only two `GLTransaction` records are 
created in one database transaction.

Two `GLTransaction` records are created to show the movement of money between the two General Ledger accounts configured 
for the transaction type.

```mermaid
sequenceDiagram
    actor user as NOMIS User
    box NOMIS
        participant NOMIS.UI as UI
        participant NOMIS.log as Sync log
        participant NOMIS.DB as Database
        participant NOMIS.sync as Sync service
    end
    box Prisoner Finance
        participant PF.sync as Sync service
        participant PF.log as Sync log
        participant PF.ledger as General Ledger
    end

    autonumber

    user ->> NOMIS.UI: Record general ledger transaction
    NOMIS.UI ->>+ NOMIS.DB: INSERT <GLTransaction>
    NOMIS.DB ->> NOMIS.DB: Trigger <GLTransactionEvent>
    NOMIS.sync ->> NOMIS.DB: SELECT <GLTransactionEvent>
    activate NOMIS.sync
    NOMIS.sync ->>+ PF.sync: POST <GLTransaction>
    PF.sync ->>+ PF.log: Create <Transaction> log
    PF.log ->>- PF.sync: Success response
    PF.sync ->>+ PF.ledger: Create <Transaction>
    PF.ledger -->>- PF.sync: Success response
    PF.sync ->>- NOMIS.sync: Success response
```

### Verify General Ledger transaction

```mermaid
sequenceDiagram
    actor user as NOMIS User
    box NOMIS
        participant NOMIS.sync as Sync service
        participant NOMIS.log as Sync log
        participant NOMIS.DB as Database
    end
    box Prisoner Finance
        participant PF.sync as Sync service
        participant PF.log as Sync log
        participant PF.ledger as General Ledger
    end

    autonumber

    NOMIS.sync ->>+ PF.sync: GET <GLTransaction>
    PF.sync ->>+ PF.ledger: GET <Transaction>
    PF.ledger ->>- PF.sync: Return <Transaction>
    PF.sync ->>- NOMIS.sync: Return <GLTransaction>
    NOMIS.sync ->>+ NOMIS.DB: SELECT <GLTransaction>
    NOMIS.DB -->>- NOMIS.sync: Return <GLTransaction>
    NOMIS.sync ->> NOMIS.sync: Compare
    opt When transactions fail to match
        NOMIS.sync ->> NOMIS.log: Create <SyncError> log
    end
```
