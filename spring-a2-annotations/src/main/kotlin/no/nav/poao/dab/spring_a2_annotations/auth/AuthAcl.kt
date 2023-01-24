package no.nav.poao.dab.spring_a2_annotations.auth

import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.Fnr

interface AuthAcl {
    fun hasToken(): Boolean
    fun sjekkTilgang(fnr: Fnr?) // Azure m2m, azure obo, tokenX obo, sts
    fun sjekkTilgang(aktorId: AktorId?) // Azure m2m, azure obo, tokenX obo, sts
}