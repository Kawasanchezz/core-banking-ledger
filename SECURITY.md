# Política de segurança

Projeto acadêmico de demonstração de um core banking (contas, transações e ledger de dupla entrada).
Não foi submetido a auditoria externa nem a teste de intrusão e **não deve processar dinheiro ou dados pessoais reais**.

## Versões suportadas

Apenas a versão mais recente do branch `main` recebe correções.

## Como reportar uma vulnerabilidade

Use o **relato privado de vulnerabilidades do GitHub** (aba *Security* do repositório, *Report a vulnerability*).
Não abra issue pública com detalhes de exploração.

Inclua: o que foi encontrado, passos para reproduzir, impacto percebido e versão/commit.
Resposta inicial esperada em até 7 dias; correção ou plano de mitigação em até 30 dias, conforme a gravidade.

## Controles implementados

- Autenticação JWT (HS256, expiração de 60 min), senhas com BCrypt, segredo do JWT exigido por variável de ambiente com mínimo de 32 bytes.
- Autorização por dono da conta em todas as rotas de conta, extrato, saldo e reconciliação (acesso a conta alheia retorna 403).
- Freio a força bruta no login: 10 falhas por e-mail ou 30 por IP em 15 minutos bloqueiam novas tentativas (429).
- Limite de 16 KiB no corpo das requisições; paginação limitada a 100 itens.
- Cabeçalhos de segurança: CSP, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`.
- Respostas de erro sem stack trace, no formato RFC 7807, com `correlationId`.
- Integridade financeira garantida no banco: ledger append-only por trigger e débitos = créditos verificado no commit.
- Contêiner sem root (uid 10001), sistema de arquivos somente leitura, sem capabilities, portas publicadas só em `127.0.0.1`.
- Dependências atualizadas e imagens fixadas por digest; Dependabot, CodeQL e varredura de segredos (gitleaks) no CI.

## Limitações conhecidas

- **Limite de tentativas em memória**: vale por instância; com várias réplicas é multiplicado. Atrás de proxy reverso, o IP visto é o do proxy, a menos que o encaminhamento de cabeçalhos seja configurado e confiável. Em produção, complemente com rate limit no gateway.
- **Token no front-end**: a página de demonstração guarda o JWT em `sessionStorage`, legível por JavaScript. A CSP restrita e a ausência de `innerHTML` reduzem o risco de XSS, mas um cookie `HttpOnly` seria mais robusto.
- **Enumeração de CPF/e-mail**: o cadastro informa quando CPF ou e-mail já existem (necessário para a usabilidade do cadastro).
- **Sem revogação de token**: um JWT emitido vale até expirar; não há logout no servidor nem refresh token.
- **Sem MFA**, sem política de complexidade de senha além do tamanho mínimo de 8 caracteres.
- **TLS**: a aplicação serve HTTP. Em produção use um proxy reverso com HTTPS (o HSTS só é emitido em requisições seguras).
- **Rate limit geral**: apenas o login tem limite; operações autenticadas não têm limite de frequência.
- O Swagger UI fica ligado no `docker-compose.yml` para demonstração (`SWAGGER_ENABLED=true`); o padrão do perfil `prod` é desligado.

## Boas práticas para quem publicar o repositório

Habilite no GitHub: secret scanning com push protection, relato privado de vulnerabilidades, proteção do branch `main` com revisão obrigatória e alertas do Dependabot.
Para bloquear segredos antes do commit: `pip install pre-commit && pre-commit install` (usa o gitleaks, ver `.pre-commit-config.yaml`).
