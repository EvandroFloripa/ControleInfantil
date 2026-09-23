package com.controleinfantil.kids.remote

/**
 * Configuração do backend Supabase.
 *
 * PREENCHA estes valores com os dados do SEU projeto Supabase (Project Settings ->
 * API). Use a chave **anon** aqui; a segurança de quem pode ler/escrever é feita
 * pelas políticas de RLS definidas em `supabase/schema.sql`.
 *
 * Dica: para não versionar segredos, você pode mover isto para `local.properties` /
 * BuildConfig depois. Por enquanto está aqui para facilitar o primeiro build.
 */
object SupabaseConfig {
    const val URL = "https://SEU-PROJETO.supabase.co"
    const val ANON_KEY = "COLE_SUA_ANON_KEY_AQUI"

    /** REST base do PostgREST. */
    val restUrl: String get() = "$URL/rest/v1"
}
