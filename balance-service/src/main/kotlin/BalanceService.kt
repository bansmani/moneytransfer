//@file:Suppress("unused")

import java.math.BigDecimal
import java.time.Instant
import java.util.*



object BalanceService {

    fun getBalance(accNumber: Long): Double {
        val queryById = CrudRepsitory.queryById(accNumber, BalanceCache::class.java)
        return queryById?.balanceAmount ?: 0.0
    }

    fun updateBalance(accountEntry: AccountEntry): Boolean {
        //should be a REST call, but testing it should be fine
        val lock = acquireBalanceCacheUpdateLock(accountEntry.accNumber)
        updateBalanceWithNoLock(accountEntry)
        LockMangerService.releaseLock(lock)
        return true
    }

    fun refreshCache(accNumber: Long): Boolean {
        //should be a REST call, but testing it should be fine
        val lock = acquireBalanceCacheUpdateLock(accNumber)
        refreshCacheWithNoLock(accNumber)
        LockMangerService.releaseLock(lock)
        return true
    }

    fun refreshCacheWithNoLock(accNumber: Long) {
        val creditSumSQL =
            "SELECT SUM(amount) from AccountEntry where transactionType='CREDIT' and accNumber='$accNumber'"
        val totalCredit = BigDecimal(CrudRepsitory.query(creditSumSQL).apply { next() }.getDouble(1))

        val debitSumSQL =
            "SELECT SUM(amount) from AccountEntry where transactionType='DEBIT' and accNumber='$accNumber'"
        val totalDebit = BigDecimal(CrudRepsitory.query(debitSumSQL).apply { next() }.getDouble(1))

        CrudRepsitory.update(
            BalanceCache(accNumber, totalCredit.subtract(totalDebit).toDouble(), Instant.now(), "cache update")
        )
    }

    fun updateBalanceWithNoLock(accountEntry: AccountEntry): Boolean {
        val balanceCache: BalanceCache? = CrudRepsitory.queryById(accountEntry.accNumber, BalanceCache::class.java)
        if (balanceCache != null) {
            val currentBalance = BigDecimal(balanceCache.balanceAmount)
            val newBalance = if (accountEntry.transactionType == InstructionType.DEBIT) {
                currentBalance.subtract(BigDecimal(accountEntry.amount)).toDouble()
            } else {
                currentBalance.add(BigDecimal(accountEntry.amount)).toDouble()
            }
            CrudRepsitory.update(
                BalanceCache(accountEntry.accNumber, newBalance, Instant.now(), accountEntry.transactionId)
            )
        } else {
            if (accountEntry.transactionType == InstructionType.DEBIT) {
                //TODO: the question is Neg balance allowed
                CrudRepsitory.save(
                    BalanceCache(
                        accountEntry.accNumber,
                        -accountEntry.amount,
                        Instant.now(),
                        accountEntry.transactionId
                    ), true
                )
            } else {
                CrudRepsitory.save(
                    BalanceCache(
                        accountEntry.accNumber, accountEntry.amount, Instant.now(), accountEntry.transactionId
                    ), true
                )
            }
        }
        return true
    }

    fun acquireBalanceCacheUpdateLock(accNumber: Long): String {
        val lock = "${accNumber}_UPDATE_BALANCE_LOCK"
        LockMangerService.acquireLock(lock, 5000)
        return lock
    }

    fun acquireAccountEntryLock(accNumber: Long) {
        acquireBalanceCacheUpdateLock(accNumber)
        LockMangerService.acquireLock(accNumber.toString() + "_DEBIT_ENTRY_LOCK", 2000)
    }

    fun releaseAccountEntryLock(accNumber: Long) {
        LockMangerService.releaseLock(accNumber.toString() + "_DEBIT_ENTRY_LOCK")
        LockMangerService.releaseLock(accNumber.toString() + "_UPDATE_BALANCE_LOCK")
    }

}
//interface IBalanceService {
//    fun updateBalance(accountEntry: AccountEntry): Boolean
//    fun updateBalanceWithNoLock(accountEntry: AccountEntry): Boolean
//    fun getBalance(accNumber: Long): Double
//    fun refreshCache(accNumber: Long): Boolean
//    fun refreshCacheWithNoLock(accNumber: Long)
//}
