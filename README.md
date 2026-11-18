# java-banking-ledger

Object-oriented account, withdrawal fee strategy, and transfer engine written in **Java 21**. Monetary values use signed `long` minor units (pence) with `BigInteger` intermediate arithmetic where products could otherwise overflow 64 bits.

## Architecture

- **Account hierarchy (`Account`, `CheckingAccount`, `SavingsAccount`)**: Abstract base class validates transaction IDs and positive amounts, uses exact `Math.addExact` / `Math.subtractExact` arithmetic, guards against signed long underflow on withdrawals, and appends immutable `TransactionEntry` records to an unmodifiable statement.
- **Composable fee policies (`FeePolicy`)**: Functional strategy interface supporting zero-fee, flat-fee, and overflow-safe tiered basis-point fees above a free allowance.
- **Transfer coordinator (`LedgerService`)**: Debits source account upfront, credits destination atomically with full principal-plus-fee reversal if the credit fails, and applies monthly `SavingsAccount` interest payouts via Java 21 pattern matching.

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
