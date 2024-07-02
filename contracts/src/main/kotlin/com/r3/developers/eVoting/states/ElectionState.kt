package com.r3.developers.eVoting.states

import com.r3.developers.eVoting.contracts.ElectionContract
import com.r3.developers.eVoting.contracts.VoterContract
import com.r3.developers.eVoting.models.ElectionRegistrationModel
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.time.Instant
import java.util.*

@BelongsToContract(ElectionContract::class)
data class ElectionState(
    var electionRegistrationModel: ElectionRegistrationModel? = null,
    val candidateIDList: MutableList<UUID>,
    var Status: String,
    //val electionID: UUID,
    val voterPublicKey: PublicKey, // Voter public key
    val electionAuthorityPublicKey: PublicKey,
    val candidatePublicKey: PublicKey,
    val auditorPublicKey: PublicKey,
    val observerPublicKey: PublicKey,
    var electionID: UUID = UUID.randomUUID(),
    private val participants: List<PublicKey>
)
    : ContractState {

    override fun getParticipants(): List<PublicKey> = participants
}