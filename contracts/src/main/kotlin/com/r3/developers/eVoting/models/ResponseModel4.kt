package com.r3.developers.eVoting.models

import net.corda.v5.base.annotations.CordaSerializable
import java.util.*

@CordaSerializable
data class ResponseModel4(

    val transactionId: String?,
    val candidateID: UUID?,
    val voteCount: Int?,
    val error : String?

)