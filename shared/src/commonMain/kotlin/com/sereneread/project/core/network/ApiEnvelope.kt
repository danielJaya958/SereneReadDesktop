package com.sereneread.project.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The wrapper around every success response. Lists without a page parameter have no [meta]. */
@Serializable
data class ApiEnvelope<T>(
    val data: T? = null,
    val meta: PageMeta? = null,
)

@Serializable
data class PageMeta(
    val page: Int,
    val limit: Int,
    @SerialName("total_items") val totalItems: Int,
    @SerialName("has_next") val hasNext: Boolean,
)
