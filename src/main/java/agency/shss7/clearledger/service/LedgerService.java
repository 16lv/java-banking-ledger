package agency.shss7.clearledger.service;

import agency.shss7.clearledger.model.Account;
import agency.shss7.clearledger.model.CheckingAccount;
import agency.shss7.clearledger.model.FeePolicy;
import agency.shss7.clearledger.model.SavingsAccount;
import agency.shss7.clearledger.model.TransactionEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates multi-account operations such as atomic transfers and month-end
 * interest runs across polymorphic Account instances.
 */
public final class LedgerService {
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final AtomicLong txSequence = new AtomicLong(1000L);

    public void registerAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account cannot be null.");
        }
        if (accounts.containsKey(account.getAccountId())) {
            throw new IllegalArgumentException("Duplicate account ID: " + account.getAccountId());
        }
        accounts.put(account.getAccountId(), account);
    }

    public Account getAccount(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new NoSuchElementException("Unknown account ID: " + accountId);
        }
        return account;
    }

    public Collection<Account> listAccounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    /**
     * Transfers funds between two registered accounts.
     * Debits the source account first so overdraft/reserve rules are checked before
     * crediting the destination account.
     */
    public String transfer(String fromAccountId, String toAccountId, long amountPence, String reference) {
        if (fromAccountId == null || fromAccountId.isBlank() || toAccountId == null || toAccountId.isBlank()) {
            throw new IllegalArgumentException("Source and destination account IDs are required.");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }
        if (amountPence <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive.");
        }
        String cleanRef = (reference == null || reference.isBlank()) ? "Account transfer" : reference.trim();

        Account source = getAccount(fromAccountId);
        Account target = getAccount(toAccountId);
        String txId = "TX-" + txSequence.incrementAndGet();

        long balanceBeforeDebit = source.getBalancePence();
        source.withdraw(txId + "-OUT", amountPence, "Transfer to " + toAccountId + ": " + cleanRef);
        long totalDebited = Math.subtractExact(balanceBeforeDebit, source.getBalancePence());
        try {
            target.deposit(txId + "-IN", amountPence, "Transfer from " + fromAccountId + ": " + cleanRef);
        } catch (RuntimeException ex) {
            source.deposit(txId + "-REV", totalDebited, "Reversal of failed transfer to " + toAccountId);
            throw ex;
        }
        return txId;
    }

    /**
     * Iterates over all registered accounts and triggers monthly interest accrual
     * on any SavingsAccount instances using Java 21 pattern matching for instanceof.
     */
    public long runMonthlyInterestCycle() {
        long totalInterestPaidPence = 0L;
        for (Account account : accounts.values()) {
            if (account instanceof SavingsAccount savings) {
                String txId = "INT-" + txSequence.incrementAndGet();
                totalInterestPaidPence = Math.addExact(
                    totalInterestPaidPence,
                    savings.applyMonthlyInterest(txId)
                );
            }
        }
        return totalInterestPaidPence;
    }

    public long getTotalHoldingsPence() {
        long total = 0L;
        for (Account account : accounts.values()) {
            total = Math.addExact(total, account.getBalancePence());
        }
        return total;
    }

    public List<TransactionEntry> getStatementByType(
        String accountId,
        TransactionEntry.EntryType entryType
    ) {
        Objects.requireNonNull(entryType, "entryType cannot be null.");
        Account account = getAccount(accountId);
        return account.getStatement()
            .stream()
            .filter(entry -> entry.type() == entryType)
            .toList();
    }

    public long getTotalFeesChargedPence(String accountId) {
        long totalFees = 0L;
        for (TransactionEntry entry : getStatementByType(accountId, TransactionEntry.EntryType.FEE)) {
            totalFees = Math.addExact(totalFees, entry.amountPence());
        }
        return totalFees;
    }

    /**
     * Exports an account's immutable statement ledger as RFC 4180-compatible CSV text.
     */
    public String exportStatementCsv(String accountId) {
        Account account = getAccount(accountId);
        StringBuilder csv = new StringBuilder();
        csv.append("transaction_id,account_id,type,amount_pence,balance_after_pence,description,timestamp\n");
        for (TransactionEntry entry : account.getStatement()) {
            csv.append(escapeCsv(entry.transactionId())).append(',')
                .append(escapeCsv(entry.accountId())).append(',')
                .append(entry.type().name()).append(',')
                .append(entry.amountPence()).append(',')
                .append(entry.balanceAfterPence()).append(',')
                .append(escapeCsv(entry.description())).append(',')
                .append(entry.timestamp()).append('\n');
        }
        return csv.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String formatGbp(long pence) {
        long abs = Math.abs(pence);
        String sign = pence < 0 ? "-" : "";
        return String.format("%sGBP %d.%02d", sign, abs / 100, abs % 100);
    }

    public static void main(String[] args) {
        LedgerService ledger = new LedgerService();
        CheckingAccount checking = new CheckingAccount(
            "CHK-01", "Jordan Taylor", 15_000L, 5_000L, FeePolicy.flatFee(25L)
        );
        SavingsAccount savings = new SavingsAccount(
            "SAV-01", "Jordan Taylor", 200_000L, 480, 10_000L
        );

        ledger.registerAccount(checking);
        ledger.registerAccount(savings);

        checking.withdraw("W-101", 18_000L, "Cloud server hosting invoice");
        ledger.transfer("SAV-01", "CHK-01", 25_000L, "Current account top-up");
        ledger.runMonthlyInterestCycle();

        System.out.println("=== JAVA BANKING LEDGER: ACCOUNT & STATEMENT SUMMARY ===");
        for (Account acc : ledger.listAccounts()) {
            System.out.printf(
                "%s [%s] Owner: %s | Closing Balance: %s%n",
                acc.getAccountId(),
                acc.getAccountTypeLabel(),
                acc.getOwnerName(),
                formatGbp(acc.getBalancePence())
            );
            for (TransactionEntry entry : acc.getStatement()) {
                System.out.printf(
                    "  %-12s %-11s %12s -> After: %12s (%s)%n",
                    entry.transactionId(),
                    entry.type(),
                    formatGbp(entry.amountPence()),
                    formatGbp(entry.balanceAfterPence()),
                    entry.description()
                );
            }
            System.out.println();
        }
    }
}
