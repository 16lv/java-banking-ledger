# java-banking-ledger

Object-oriented account, withdrawal fee strategy, and transfer engine written in **Java 21**. Monetary values use signed `long` minor units (pence) throughout to avoid binary floating-point rounding drift.

## Architecture

- **Account hierarchy (`Account`, `CheckingAccount`, `SavingsAccount`)**: Abstract base class validates transaction IDs and positive amounts before mutating balances, delegates withdrawal eligibility via `canWithdraw()`, and records immutable `TransactionEntry` records on every credit, debit, fee, or interest payout.
- **Composable fee policies (`FeePolicy`)**: Functional strategy interface supporting zero-fee, flat-fee, and tiered basis-point fee calculation above a free threshold.
- **Transfer coordinator (`LedgerService`)**: Validates source/destination accounts upfront, debits and credits across accounts, and calculates monthly interest payouts across `SavingsAccount` instances using Java 21 pattern matching.

## Structure

```text
java-banking-ledger/
├── src/main/java/agency/shss7/clearledger/
│   ├── model/
│   │   ├── Account.java
│   │   ├── CheckingAccount.java
│   │   ├── SavingsAccount.java
│   │   ├── FeePolicy.java
│   │   └── TransactionEntry.java
│   └── service/
│       └── LedgerService.java
└── src/test/java/agency/shss7/clearledger/service/
    └── LedgerServiceTest.java
```

## Build & Run

```bash
mvn test
mvn -q package
java -jar target/clearledger-1.0.0.jar
```
