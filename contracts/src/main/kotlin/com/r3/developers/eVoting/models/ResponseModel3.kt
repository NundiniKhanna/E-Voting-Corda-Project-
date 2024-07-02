package com.r3.developers.eVoting.models

import net.corda.v5.base.annotations.CordaSerializable
import java.util.*

@CordaSerializable
data class ResponseModel3(

    val transactionId: String?,
    val electionID: UUID?,
    val error : String?

)