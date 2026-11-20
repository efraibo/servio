# Definition of Ready e Definition of Done — Servio

> Este documento vale para humanos e para agentes de IA. Uma estória só entra em sprint se cumprir a DoR, e só é fechada se cumprir a DoD.

## Formato da estória

```
Título: <verbo no infinitivo + resultado> (ex.: "Cadastrar cliente com telefone obrigatório")

Como <persona do PRD>
Quero <ação>
Para <benefício>

Contexto
- Épico: <E1..E5> · Feature do PRD: <ex.: E2.1>
- Regras de negócio: <RN-xx do PRD>

Critérios de aceite (Gherkin, cada um vira pelo menos um teste automatizado)
Cenário 1: <caminho feliz>
  Dado ...
  Quando ...
  Então ...
Cenário 2: <regra ou erro>
  ...

Fora do escopo
- <o que esta estória NÃO faz>

Notas técnicas (opcional, só o que for decisão já tomada)
- <ADR relacionado, endpoint esperado, tela>
```

## Definition of Ready (DoR)

Uma estória está pronta para desenvolvimento quando:

- [ ] Está ligada a um épico e a uma feature do PRD
- [ ] Persona é uma das do PRD (Gestor, Atendente, Mecânico)
- [ ] Tem de 2 a 6 critérios de aceite em Gherkin, todos verificáveis por teste
- [ ] Inclui ao menos um cenário de erro ou de regra de negócio
- [ ] Cita as RN do PRD que se aplicam
- [ ] Tem "fora do escopo" explícito
- [ ] Cabe em até 3h de trabalho (se não couber, quebrar)
- [ ] Não depende de decisão em aberto (se depender, criar antes a ADR)

## Definition of Done (DoD)

- [ ] Todos os critérios de aceite cobertos por testes automatizados, passando
- [ ] Build e testes verdes no CI
- [ ] Isolamento multi-tenant testado quando a estória acessa dados
- [ ] Sem dados reais ou segredos no código, nos testes ou nos logs
- [ ] PR com ID da estória (`SRV-xx`) no título. Commits `feat`, `fix`, `test` e `refactor` também levam o ID; `docs`, `chore`, `ci` e `build` são isentos (ADR-0003)
- [ ] Revisão do agente revisor + revisão humana em regra de negócio
- [ ] Docs atualizados quando a estória muda regra, API ou decisão
