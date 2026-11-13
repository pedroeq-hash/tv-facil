package br.tvfacil.core

enum class Tipo { AO_VIVO, FILME }

data class Canal(
    val nome: String,
    val url: String,
    val grupo: String = "",
    val logo: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    val tipo: Tipo = Tipo.AO_VIVO,
)
