package com.r3.developers.eVoting.models



import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.TransactionState
import java.util.*

@CordaSerializable
data class ResponseModel(
    val transactionId: String?,
    val winnerCandidateId: UUID?,
    val voteCount: Int?,
    val error: String?
)