package com.r3.developers.eVoting.states

import com.r3.developers.eVoting.contracts.CandidateContract
import com.r3.developers.eVoting.models.CandidateProfile
import com.r3.developers.eVoting.models.CandidateVoteCountModel
import com.r3.developers.eVoting.models.DeclareWinnerModel
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

@BelongsToContract(CandidateContract::class)
data class CandidateState(
    var status: String,

    val candidateVoteCountModel: CandidateVoteCountModel? = null,
    val declareWinnerModel : DeclareWinnerModel?= null,
    val voterPublicKey: PublicKey,
    val candidateProfile: CandidateProfile?=null,
    val candidatePublicKey: PublicKey,
    val electionAuthorityPublicKey: PublicKey,
    val auditorPublicKey: PublicKey,
    val observerPublicKey: PublicKey,
    val registrationTime: Long?= null,
    val candidateID: UUID = UUID.randomUUID(),
    private val participants: List<PublicKey>
) : ContractState {

    override fun getParticipants(): List<PublicKey> = participants
}
