package no.nav.poao.dab.spring_auth

import com.nimbusds.jwt.JWTClaimsSet
import io.kotest.assertions.throwables.shouldThrow
import no.nav.common.auth.Constants
import no.nav.common.auth.context.UserRole
import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.EksternBrukerId
import no.nav.common.types.identer.Fnr
import no.nav.poao.dab.spring_auth.EksternBrukerAuth.harEksternBrukerHarTilgang
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class EksternBrukerAuthTest {

    val mockPersonService = object: IPersonService {
        override fun getFnrForAktorId(aktorId: EksternBrukerId): Fnr {
            return Fnr.of("12121212121")
        }
        override fun getAktorIdForPersonBruker(eksternBrukerId: EksternBrukerId): AktorId {
            return AktorId("12121212121")
        }
    }
    val brukerId = Fnr.of("12121212121")

    @Test
    fun skal_tillate_bruker_med_riktig_fnr_i_token() {
        harEksternBrukerHarTilgang(
            NavPrincipal.of(
                JWTClaimsSet.Builder()
                    .claim("acr", "Level4")
                    .claim(Constants.ID_PORTEN_PID_CLAIM, brukerId.get()).build(),
                UserRole.EKSTERN
            ) as EksternBrukerPrincipal,
            brukerId
        ).thorwIfIkkeTilgang()
    }

    @Test
    fun skal_blokkere_bruker_med_annet_fnr_i_token() {
        shouldThrow<ResponseStatusException> {
            harEksternBrukerHarTilgang(
                NavPrincipal.of(
                    JWTClaimsSet.Builder()
                        .claim("acr", "Level4")
                        .claim(Constants.ID_PORTEN_PID_CLAIM, brukerId.get()).build(),
                    UserRole.EKSTERN
                ) as EksternBrukerPrincipal,
                Fnr.of("21212121212")
            ).thorwIfIkkeTilgang()
        }
    }

    @Test
    fun skal_blokkere_brukere_uten_nivå_4() {
        shouldThrow<ResponseStatusException> {
            harEksternBrukerHarTilgang(
                NavPrincipal.of(
                    JWTClaimsSet.Builder()
                        .claim("acr", "Level3")
                        .claim(Constants.ID_PORTEN_PID_CLAIM, brukerId.get()).build(),
                    UserRole.EKSTERN
                ) as EksternBrukerPrincipal,
                Fnr.of("21212121212")
            ).thorwIfIkkeTilgang()
        }
    }
}
