@file:Suppress("unused")

import java.time.Instant
import java.util.*

data class InstructionDTO(
    val accNumber: Long,
    val amount: Double,
    val instructionType: InstructionType,
    val description: String? = null
)

data class BalanceCache(
    @Id val accNumber: Long, val balanceAmount: Double,
    val updateTime: Instant,
    val updatedRef: String
)


data class LocalTransferInstructionDTO(
    val fromAccNumber: Long,
    val toAccNumber: Long,
    val amount: Double,
    val description: String? = null
)

fun LocalTransferInstructionDTO.toDebitInstructionDTO() =
    InstructionDTO(
        fromAccNumber,
        amount,
        InstructionType.DEBIT,
        description
    )

fun LocalTransferInstructionDTO.toCreditInstructionDTO() =
    InstructionDTO(
        toAccNumber,
        amount,
        InstructionType.CREDIT,
        description
    )


enum class InstructionType {
    DEBIT,
    CREDIT
}

class Transaction(
    val instructionType: InstructionType,
    val accNumber: Long, val amount: Double, val description: String? = "",
    val parentTransactionId: String? = null
) {

    @Id
    val transactionId = UUID.randomUUID().toString()
    val initiateTime: Instant = Instant.now()
    val endTime: String? = null
    val status: TransactionStatus = TransactionStatus.NEW
    val errorMessage: String = ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Transaction
        if (transactionId != other.transactionId) return false
        return true
    }

    override fun hashCode(): Int {
        return transactionId.hashCode()
    }

    override fun toString(): String {
        return "Transaction(transactionType=$instructionType, tansactionId='$transactionId', transactionStartTime=$initiateTime, transactionEndTime=$endTime, transactionStatus=$status)"
    }
}


data class AccountEntry(
    @Indexed val accNumber: Long,
    val amount: Double,
    val transactionTime: Instant,
    @Id val transactionId: String,
    @Id val transactionType: InstructionType,
    val description: String? = null
)

fun AccountEntry.getCompensatingEntry() = AccountEntry(
    accNumber,
    amount,
    Instant.now(),
    transactionId,
    if (transactionType == InstructionType.DEBIT) InstructionType.CREDIT else InstructionType.DEBIT,
    description + "Compensating transaction"
)


data class TransactionStatusDTO(
    val transactionId: String,
    val status: TransactionStatus,
    val errorMessage: String,
    val endTime: String
)

enum class TransactionStatus {
    NEW,
    COMPLETED,
    FAILED,
    ERROR,
    UNKNOWN
}


