package com.controleinfantil.kids.remote

import com.controleinfantil.kids.BuildConfig

/**
 * Configuração do backend Supabase.
 *
 * Os valores vêm do build, nunca do código versionado:
 * - **CI:** secrets `SUPABASE_URL` e `SUPABASE_ANON_KEY` do repositório.
 * - **Local:** `supabase.url` e `supabase.anonKey` no `local.properties` (fora do git).
 *
 * Use a chave **anon** (Project Settings -> API); a segurança de quem pode ler/escrever
 * é feita pelas políticas de RLS definidas em `supabase/schema.sql`.
 */
object SupabaseConfig {
    const val URL = BuildConfig.SUPABASE_URL
    const val ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
}
