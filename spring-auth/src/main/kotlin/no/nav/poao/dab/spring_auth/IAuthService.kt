package no.nav.poao.dab.spring_auth

import no.nav.common.types.identer.*

interface IAuthService {
    fun sjekkTilgangTilPerson(ident: EksternBrukerId)
    fun harTilgangTilEnhet(enhet: EnhetId): Boolean
    fun sjekkTilgangTilEnhet(enhet: EnhetId)
    fun sjekkInternbrukerHarSkriveTilgangTilPerson(aktorId: AktorId)
    fun getInnloggetVeilederIdent(): NavIdent
    fun getLoggedInnUser(): Id
    fun getInnloggetBrukerIdent(): String?
    fun sjekkAtApplikasjonErIAllowList(allowlist: Array<String>)
    fun sjekkAtApplikasjonErIAllowList(allowlist: List<String?>)
    fun erEksternBruker(): Boolean
    fun erInternBruker(): Boolean
    fun erSystemBruker(): Boolean
    fun erSystemBrukerFraAzureAd(): Boolean
}