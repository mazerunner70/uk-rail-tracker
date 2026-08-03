package com.ukrailtracker.app.data.source

enum class SourceId {
    BUNDLED_STATIONS,
}

data class SourcePayload(
    val sourceId: SourceId,
    val bytes: ByteArray,
    val contentType: String,
    val fetchedAtEpochMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourcePayload) return false
        return sourceId == other.sourceId &&
            contentType == other.contentType &&
            fetchedAtEpochMs == other.fetchedAtEpochMs &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + fetchedAtEpochMs.hashCode()
        return result
    }
}

fun interface DataSource {
    suspend fun fetch(): SourcePayload
}
