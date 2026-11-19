# java-banking-ledger

Deterministic banking domain ledger written in **Java 21** using integer-pence accounting, Strategy-pattern fee rules, overflow-safe arithmetic, and an immutable transaction trail.

## Overview

`java-banking-ledger` avoids IEEE 754 floating-point drift by storing all balances and fees in whole pence (`long`), guarding arithmetic with `Math.addExact`/`Math.subtractExact` and `BigInteger` intermediate math.

## Features

- **Account subtypes**: Abstract `Account` base class extended by `CheckingAccount` (configurable overdraft limit) and `SavingsAccount` (minimum reserve balance + basis-point monthly interest accrual).
- **Composable fee rules**: Functional `FeePolicy` interface with `zeroFee()`, `flatFee()`, and `tieredFee()` (exempt below threshold, basis-point rate on excess).
- **Overflow-safe money math**: Guards balance additions, subtractions, percentage fees, and aggregate holdings against silent 64-bit signed overflow/underflow.
- **Compensating transfer rollback**: `LedgerService.transfer()` captures total debited principal + withdrawal fee and reverses the full amount if crediting the destination fails.
- **Immutable statements**: `getStatement()` returns an unmodifiable view of `TransactionEntry` records carrying running `balanceAfterPence` checkpoints.

## Technology Stack

- Java 21 LTS
- Maven 3.9+
- JUnit 5 (Jupiter)

## Build & Run

Compile, run the CLI ledger walkthrough, and run tests:

```bash
mvn -B test
mvn -B -q compile exec:java -Dexec.mainClass="agency.shss7.clearledger.service.LedgerService"
```

## Technical Implementation Details

- Monthly savings interest converts `(balancePence * annualRateBps) / 120_000` via `BigInteger` before converting back with `longValueExact()`, keeping precision for arbitrarily large balances.
- `LedgerService.transfer()` computes `balanceBeforeDebit - source.getBalancePence()` after withdrawal so rolled-back transfers restore both principal and any tiered/flat fee charged.

## Project Structure

```text
java-banking-ledger/
├── src/main/java/agency/shss7/clearledger/
│   ├── model/
│   │   ├── Account.java
│   │   ├── CheckingAccount.java
│   │   ├── FeePolicy.java
│   │   ├── SavingsAccount.java
│   │   └── TransactionEntry.java
│   └── service/
│       └── LedgerService.java
├── src/test/java/agency/shss7/clearledger/service/
│   └── LedgerServiceTest.java
└── pom.xml
```

## Testing

```bash
mvn -B test
```

## Known Limitations & Future Improvements

- Account storage lives in an in-memory `LinkedHashMap` with no persistence backend or row-level concurrency locks; adding JDBC/PostgreSQL persistence with `SERIALIZABLE` / optimistic locking would adapt it cleanly for multi-threaded services.
- Supports GBP pence only (`long`); multi-currency transfers would pair `long` minor units with explicit currency codes.
