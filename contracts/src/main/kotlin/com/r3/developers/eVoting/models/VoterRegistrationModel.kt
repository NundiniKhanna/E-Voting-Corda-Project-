package com.r3.developers.eVoting.models

import net.corda.v5.base.annotations.CordaSerializable
import java.util.*

//import net.corda.v5.base.EncryptedBytes

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

@CordaSerializable
data class EncryptedField(val cipherText: ByteArray, val initialisationVector: ByteArray)

fun String.encrypt(keyPair: PublicKey): EncryptedField {
    val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keyPair)
    val encryptedBytes = cipher.doFinal(this.toByteArray())
    return EncryptedField(encryptedBytes, cipher.iv)
}

// Adjust the decrypt function to take cipherText and initialisationVector as parameters


    fun EncryptedField.decrypt(privateKey: PrivateKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        val ivSpec = IvParameterSpec(initialisationVector)
        cipher.init(Cipher.DECRYPT_MODE, privateKey, ivSpec)
        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes)
    }


@CordaSerializable
data class VoterRegistrationModel(
    val voterName: String,
    val mobileNumber: String,
    val email: String,
    val dateOfBirth: String,

    val encryptedVoterName: EncryptedField,
    val encryptedMobileNumber: EncryptedField,
    val encryptedEmail: EncryptedField,
    val encryptedDateOfBirth: EncryptedField,
    val registrationDate: Long? = null
)
