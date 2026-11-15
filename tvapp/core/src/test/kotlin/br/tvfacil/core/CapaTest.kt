package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CapaTest {
    @Test
    fun `capa do TMDB vira a versao pequena (185 px, ~17 KB em vez de ~106 KB)`() {
        assertEquals("https://image.tmdb.org/t/p/w185/abc.jpg", Capa.endereco("https://image.tmdb.org/t/p/w600_and_h900_bestv2/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w185/x.jpg", Capa.endereco("https://image.tmdb.org/t/p/original/x.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w185/x.jpg", Capa.endereco("https://image.tmdb.org/t/p/w185/x.jpg"))
    }

    @Test
    fun `capa de outro site fica como veio`() {
        assertEquals("https://cdn.exemplo.com/capas/titanic.jpg", Capa.endereco("https://cdn.exemplo.com/capas/titanic.jpg"))
    }

    @Test
    fun `endereco nao confiavel nao e baixado`() {
        for (ruim in listOf("", "file:///sdcard/a.jpg", "http://192.168.0.5/a.jpg", "http://localhost/a.jpg", "javascript:alert(1)", "https://a.com/" + "x".repeat(2100))) {
            assertNull(Capa.endereco(ruim), ruim)
        }
    }
}
