import org.jetbrains.annotations.TestOnly
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAmount
import java.util.*


val testDomainFactory = TestDomainModelFactory()

//Testonly is not working as expected, better to create a module and add them as testCompile to all project
class TestDomainModelFactory @TestOnly constructor() {

    fun generateAccNumber(): Long {
        Thread.sleep(1)
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddhhmmSSS")).toLong()
    }

    fun buildCreditInstructionDto() =
        InstructionDTO(generateAccNumber(), 500.0, InstructionType.CREDIT, "Tuition Fees")

    fun buildDebitInstructionDto() =
        InstructionDTO(generateAccNumber(), 300.0, InstructionType.DEBIT, "Tuition Fees")

    fun buildCreditEntry(amount: Double=700.0) = AccountEntry(generateAccNumber(),amount, Instant.now(),
            UUID.randomUUID().toString(),InstructionType.CREDIT,"Test Credit message")

    fun buildDebitEntry(amount: Double=300.0) = AccountEntry(generateAccNumber(),amount, Instant.now(),
        UUID.randomUUID().toString(),InstructionType.DEBIT,"Test Debit message")


}