# Migrações do banco (Supabase)

Este diretório guarda as alterações do banco em ordem, uma por arquivo. Cada uma é aplicada **uma vez** e registrada na tabela `public.schema_migrations`.

## Regras

1. **Nunca edite uma migração já aplicada.** Para mudar algo, crie uma nova.
2. **Um arquivo por mudança**, com número sequencial de quatro dígitos: `0007_nome.sql`.
3. **Cada arquivo termina registrando a si mesmo**:

   ```sql
   insert into public.schema_migrations (version, name)
   values ('0007', 'nome')
   on conflict (version) do nothing;
   ```

4. **Prefira mudanças aditivas** (criar, acrescentar). Evite `drop` de tabela ou coluna sem autorização expressa.
5. **Rode em transação** quando possível, para não deixar o banco pela metade.

## Como aplicar

1. Abra o **SQL Editor** do projeto no Supabase.
2. Confira a última versão aplicada:

   ```sql
   select version, name, applied_at from public.schema_migrations order by version;
   ```

3. Rode, em ordem, só os arquivos com versão maior que a última aplicada.
4. Cada arquivo registra a própria versão ao final. Se algum falhar no meio, corrija o problema antes de seguir.

## Estado atual

| Versão | Arquivo | Situação |
|---|---|---|
| 0000 | `0000_schema_migrations.sql` | Cria o registro. **Aplicar primeiro.** |
| — | `../schema.sql` | Base do banco (legado). Aplicado antes deste registro. |
| — | `../apps.sql` | Apps liberados e `uninstall_app` (legado). |
| — | `../signaling.sql` | Sinalização WebRTC (legado). |
| — | `../timerules.sql` | Horários e limites (legado). |
| — | `../usagelog.sql` | Histórico de apps (legado). |
| 0006 | `0006_realtime_wake.sql` | Aviso em tempo real de comando novo. **Pendente.** |

Os arquivos legados ficam na raiz de `supabase/` porque já foram aplicados e têm alterações de outro trabalho ainda não commitado. Não os edite. Quando eles forem commitados e revisados, podem ser movidos para cá com números, sem mudar o conteúdo.

### Registrar os legados (uma vez)

Depois de aplicar `0000`, marque os legados como já aplicados, para o histórico ficar completo:

```sql
insert into public.schema_migrations (version, name) values
    ('legacy-schema', 'schema.sql'),
    ('legacy-apps', 'apps.sql'),
    ('legacy-signaling', 'signaling.sql'),
    ('legacy-timerules', 'timerules.sql'),
    ('legacy-usagelog', 'usagelog.sql')
on conflict (version) do nothing;
```

## Por que não usar a CLI do Supabase agora

A CLI (`supabase db push`) espera este mesmo formato de pasta e controla o histórico sozinha. É a opção recomendada se o projeto for ligado à CLI no futuro: esta estrutura já está no formato compatível, só faltaria renomear os arquivos com carimbo de data. A decisão de ferramenta continua pendente (LOTE-02).
