-- Migração 0000: registro das migrações aplicadas no banco.
--
-- Rode este arquivo uma única vez, antes de qualquer outra migração. Depois disso,
-- cada migração registra a si mesma nesta tabela ao final (ver migrations/README.md).
--
-- Fica fechada para os papéis públicos (anon/authenticated): só o dono do projeto e o
-- SQL editor leem ou gravam aqui.

create table if not exists public.schema_migrations (
    version    text primary key,
    name       text not null,
    applied_at timestamptz not null default now()
);

alter table public.schema_migrations enable row level security;
revoke all on public.schema_migrations from anon, authenticated;

insert into public.schema_migrations (version, name)
values ('0000', 'schema_migrations')
on conflict (version) do nothing;
