package com.r3.developers.eVoting.contracts

import com.r3.developers.eVoting.contracts.test.privateKey
import com.r3.developers.eVoting.models.decrypt
import com.r3.developers.eVoting.states.VoterState
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PrivateKey

@CordaSerializable
class VoterContract : Contract {

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.singleOrNull()
            ?: throw CordaRuntimeException("Requires a single command.")

        val outputStates = transaction.outputContractStates.filterIsInstance(VoterState::class.java)

        when (command) {
            is Create -> verifyCreate(transaction, outputStates)
            is Login -> verifyLogin(transaction, outputStates)
            else -> throw CordaRuntimeException("Command not allowed.")
        }
    }

    private fun verifyCreate(transaction: UtxoLedgerTransaction, outputStates: List<VoterState>) {
        require(transaction.inputContractStates.isEmpty()) {
            "Create command should have no input states."
        }
        require(outputStates.size == 1) {
            "Create command should have one and only one output state."
        }
        val output = outputStates.single()

        val voterRegistrationModel = output.voterRegistrationModel
            ?: throw CordaRuntimeException("VoterRegistrationModel not found in output state.")

        val decryptedName = voterRegistrationModel.encryptedVoterName.decrypt(privateKey)
        require(decryptedName.isNotBlank()) {
            "Decrypted voter name must not be blank."
        }

        // Decrypt and validate mobile number
        val decryptedMobileNumber = voterRegistrationModel.encryptedMobileNumber.decrypt(privateKey)
        require(decryptedMobileNumber.isNotBlank()) {
            "Decrypted mobile number must not be blank."
        }

// Decrypt and validate email
        val decryptedEmail = voterRegistrationModel.encryptedEmail.decrypt(privateKey)
        require(decryptedEmail.isNotBlank()) {
            "Decrypted email must not be blank."
        }

// Decrypt and validate date of birth
        val decryptedDateOfBirth = voterRegistrationModel.encryptedDateOfBirth.decrypt(privateKey)
        require(decryptedDateOfBirth.isNotBlank()) {
            "Decrypted date of birth must not be blank."
        }


//        require(output.formerName.isNotBlank()) {
//            "The former name must not be blank."
//        }

        // Add more validation for other fields as needed.
    }


    private fun verifyLogin(transaction: UtxoLedgerTransaction, outputStates: List<VoterState>) {
        require(transaction.inputContractStates.size == 1) {
            "Login command should have one and only one input state."
        }
        require(outputStates.size == 1) {
            "Login command should have one and only one output state."
        }

        val input = transaction.inputContractStates.single() as VoterState
        val output = outputStates.single()

        require(input.voterID == output.voterID) {
            "voterID cannot be changed for Login command."
        }
        require(input.voterRegistrationModel?.email == output.voterRegistrationModel?.email) {
            "email cannot be changed"
        }
        require(input.status == "Voter-created") {
            "Only voters with status 'Voter-created' can log in."
        }

        val voterRegistrationModel = output.voterRegistrationModel
            ?: throw CordaRuntimeException("VoterRegistrationModel not found in output state.")

        val decryptedEmail = voterRegistrationModel.encryptedEmail.decrypt(privateKey)
        require(decryptedEmail.isNotBlank()) {
            "Decrypted email must not be blank."
        }
        require(input.voterRegistrationModel?.email == decryptedEmail) {
            "Decrypted email does not match the email in the input state."
        }

        // Add more custom contract rules for the Login command if needed.
    }

    // Add more custom contract rules for the Update command if needed.


    class Create : Command
    class Login : Command

}
object test {
    lateinit var privateKey: PrivateKey // Store the private key for decryption
}
