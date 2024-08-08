package no.nav.poao.dab.spring_auth

import no.nav.poao_tilgang.client.TilgangType

enum class TilgangsType {
    LESE,
    SKRIVE;

    fun toPoaoTilgangsType(): TilgangType {
        return when (this) {
            LESE -> TilgangType.LESE
            SKRIVE -> TilgangType.SKRIVE
        }
    }
}
