package com.r3.developers.eVoting.contracts

import com.r3.developers.eVoting.states.CandidateState
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

@CordaSerializable
class CandidateContract : Contract {

    override fun verify(transaction: UtxoLedgerTransaction) {
        val command = transaction.commands.singleOrNull()
            ?: throw CordaRuntimeException("Requires a single command.")

        val outputStates = transaction.outputContractStates.filterIsInstance(CandidateState::class.java)

        when (command) {
            is CreateCandidate -> verifyCreate(transaction, outputStates)
            is UpdateVoteCount -> verifyUpdate(transaction, outputStates)
            is DeclareWinner -> verifyDeclareWinner(transaction, outputStates)
            else -> throw CordaRuntimeException("Command not allowed.")
        }
    }

    private fun verifyCreate(transaction: UtxoLedgerTransaction, outputStates: List<CandidateState>) {
        require(transaction.inputContractStates.isEmpty()) {
            "Create command should have no input states."
        }
        require(outputStates.size == 1) {
            "Create command should have one and only one output state."
        }
        val output = outputStates.single()

        // Add more validation for other fields as needed.
    }

    private fun verifyUpdate(transaction: UtxoLedgerTransaction, outputStates: List<CandidateState>) {
        require(transaction.inputContractStates.isEmpty()) {
            "Create command should have no input states."
        }
        require(outputStates.size == 1) {
            "Create command should have one and only one output state."
        }

        val input = transaction.inputContractStates.single() as CandidateState
        val output = outputStates.single()

        require(input.candidateID == output.candidateID) {
            "Batch ID cannot be changed for Update command."
        }

        // Add more validation for other fields as needed.
    }

    private fun verifyDeclareWinner(transaction: UtxoLedgerTransaction, outputStates: List<CandidateState>) {
        require(transaction.inputContractStates.isEmpty()) {
            "DeclareWinner command should have no input states."
        }
        require(outputStates.size == 1) {
            "DeclareWinner command should have one and only one output state."
        }

        val output = outputStates.single()

        // Add validation logic to ensure that winner is declared correctly.
        // For example, you can check if the winner state is correctly updated.

        // Add more validation for other fields as needed.
    }

    class CreateCandidate : Command

    class UpdateVoteCount : Command

    class DeclareWinner : Command
}
