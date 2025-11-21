# 2. timestamp-standardisation

Date: 2025-10-27

## Status

Accepted

## Context

The new Prisoner Finance Ledger system is designed to replace a legacy system that records financial transactions using a `LocalDateTime` data type. This legacy data lacks timezone information, meaning that timestamps are tied to the local timezone of the prison where the transaction occurred (e.g., `Europe/London`).

In the new system, all transaction events are considered a core source of truth. To ensure consistency, accuracy, and auditability across a distributed, national system, it is critical to have a standardised approach to handling time. Discrepancies in time data can lead to reconciliation issues, incorrect balance calculations, and difficulties with historical analysis.

## Decision

All timestamps within the new system will be stored and processed as a universally consistent `Instant` or UTC `Timestamp`.

We will implement a dedicated `TimeConversionService` to handle the conversion of all incoming legacy `LocalDateTime` values to UTC `Instant` before they are persisted to the database. This service will be the single source of truth for the legacy timezone (`Europe/London`).

This decision applies to:

- All new financial transaction records.
- The reconciliation process for migrating legacy balances.
- All subsequent data synchronisation from the legacy system.

## Consequences

### Positive

- **Guaranteed Data Integrity:** Storing all timestamps as UTC eliminates ambiguity and removes the risk of reconciliation errors caused by Daylight Saving Time (DST) or timezone differences.
- **Improved Auditability:** A single, consistent time standard simplifies the process of auditing the ledger. Transactions can be ordered chronologically across all prisons and systems without needing to perform complex timezone conversions.
- **Simplified Business Logic:** All services, particularly the core `LedgerQueryService` that calculates balances, can operate on a single time format. This simplifies filtering, sorting, and comparison logic.
- **Alignment with Best Practices:** Using UTC as the universal standard for timestamps is a widely accepted practice in distributed systems and microservices architectures.
- **Centralised Time Management:** The use of a `TimeConversionService` centralises the conversion logic, making it easy to manage and update if the legacy system's timezone ever changes.

### Negative

- **Initial Development Overhead:** We must create and test the `TimeConversionService` and ensure all data ingestion points correctly use it.
- **Irreversible Conversion:** Once converted to UTC, the original, non-timezone-aware `LocalDateTime` from the legacy system is not retained. However, this is an acceptable tradeoff given the benefits. The source system remains the authority for the local time if that information is ever needed.
    
