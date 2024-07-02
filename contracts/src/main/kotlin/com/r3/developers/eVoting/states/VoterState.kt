package com.r3.developers.eVoting.states

import com.r3.developers.eVoting.contracts.VoterContract
import com.r3.developers.eVoting.models.LoginModel
import com.r3.developers.eVoting.models.VoterRegistrationModel
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*

@BelongsToContract(VoterContract::class)
data class VoterState(
    //val name: String,

    var voterRegistrationModel: VoterRegistrationModel?=null,
    var loginModel : LoginModel?=null,
    val voterID: UUID = UUID.randomUUID(),
    var status: String,
    var candidateID:  UUID?=null,
    val voterPublicKey: PublicKey, // Voter public key
    val electionAuthorityPublicKey: PublicKey,
    val candidatePublicKey: PublicKey,
    val auditorPublicKey: PublicKey,
    val observerPublicKey: PublicKey,
    private val participants: List<PublicKey>

) : ContractState {

    override fun getParticipants(): List<PublicKey> = participants

}
