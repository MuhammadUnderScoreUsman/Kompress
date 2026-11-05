package com.musman.kompression

import kotlinx.serialization.Serializable


@Serializable
sealed class CompressRoute {

    @Serializable
    data object Home: CompressRoute()

    @Serializable
    data object Single: CompressRoute()

    @Serializable
    data object Batch: CompressRoute()
}