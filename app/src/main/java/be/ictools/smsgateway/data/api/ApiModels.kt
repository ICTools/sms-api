package be.ictools.smsgateway.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteMessageDto(
    @SerialName("ulid") val ulid: String,
    @SerialName("phoneNumber") val phoneNumber: String,
    @SerialName("body") val body: String,
    @SerialName("direction") val direction: String,
    @SerialName("createdAt") val createdAt: String? = null,
)

@Serializable
data class IncomingMessageRequest(
    @SerialName("phoneNumber") val phoneNumber: String,
    @SerialName("body") val body: String,
    @SerialName("sentAt") val sentAt: String,
    @SerialName("deviceMessageId") val deviceMessageId: Long? = null,
)

@Serializable
data class OutgoingMessageRequest(
    @SerialName("phoneNumber") val phoneNumber: String,
    @SerialName("body") val body: String,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("deviceMessageId") val deviceMessageId: Long? = null,
)

@Serializable
data class MessageStatusRequest(
    @SerialName("status") val status: String,
    @SerialName("deviceMessageId") val deviceMessageId: Long? = null,
    @SerialName("sentAt") val sentAt: String? = null,
    @SerialName("deliveredAt") val deliveredAt: String? = null,
    @SerialName("errorMessage") val errorMessage: String? = null,
)

@Serializable
data class MercureEnvelope(
    @SerialName("ulid") val ulid: String,
)
