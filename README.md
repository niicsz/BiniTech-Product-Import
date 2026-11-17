# BiniTech Product Import

[![CI](https://github.com/niicsz/BiniTech-Product-Import/actions/workflows/ci.yml/badge.svg)](https://github.com/niicsz/BiniTech-Product-Import/actions/workflows/ci.yml)

Microserviço API-first para importação e migração de produtos por CSV, XLSX e XLS. O serviço é independente do backend do PDV, segue arquitetura hexagonal e possui banco MongoDB exclusivo. O frontend continua sendo a SPA Angular do BiniTech PDV.

## Fluxo

1. O bearer token é validado no BiniTech Auth.
2. O PDV confirma usuário, role e tenant ativos; o tenantId nunca é recebido do frontend.
3. O arquivo é salvo no GridFS do Mongo exclusivo e os cabeçalhos são sugeridos.
4. A validação completa normaliza BigDecimal, identifica erros, duplicidades e produtos existentes em lote.
5. O usuário revisa, decide manter/atualizar por produto e confirma o tratamento de estoque.
6. O ImportJob é publicado no RabbitMQ e processado em lotes de até 250 registros.
7. O frontend consulta progresso e baixa o relatório CSV com erros.

Os estados são PENDING, PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED e CANCELLED.

Administradores da plataforma sem tenant não podem importar produtos: a API retorna
403 `TENANT_REQUIRED`, orientando a usar uma conta da loja de destino. Isso não é
expiração de sessão e não deve iniciar refresh ou logout no frontend.

## Arquitetura

    adapters/inbound/web + messaging
                     |
    application/ports/inbound
                     |
    application/usecases + domain
                     |
    application/ports/outbound
                     |
    adapters/outbound/file + http + messaging + persistence

Apache POI, Commons CSV, Spring MVC, MongoDB e RabbitMQ existem somente nos adapters. O domínio e os casos de uso não dependem dessas bibliotecas.

## API-first

O contrato fonte é src/main/resources/openapi/swagger.yaml. As interfaces e DTOs HTTP são gerados no build:

    mvn generate-sources
    mvn test

Swagger UI: /swagger-ui.html. OpenAPI gerado: /api-docs.

## Consistência e desempenho

- Arquivo e jobs ficam no banco exclusivo; produtos continuam pertencendo somente ao banco do PDV.
- Consultas por código de barras e gravações são em lote, sem N+1 e sem um save por produto.
- O consumer usa compare-and-set para um ImportJob não ser iniciado duas vezes.
- Cada comando recebe operationId igual a jobId:linha; recibos com índice único impedem repetição sequencial.
- Código de barras + tenant possui definição de índice único no PDV. Antes de criar o índice em base antiga, audite duplicidades.
- Sem transação distribuída entre bancos: falhas são registradas por linha, sem fingir atomicidade entre serviços.
- Estoque é ignorado por padrão. Substituir ou incrementar exige confirmação; valores anterior/importado ficam na auditoria.

O domínio legado do PDV ainda usa double para preço. O importador mantém dinheiro em BigDecimal/Mongo Decimal128 e só converte no boundary interno do PDV. A migração global de produtos, vendas e contratos públicos deve ser feita separadamente.

## Rodar localmente com Docker

    cp .env.example .env
    docker compose up --build

O compose sobe o MongoDB do importador em 127.0.0.1:27018 e o serviço em http://localhost:8082. Ele não sobe RabbitMQ, Auth nem PDV: usa os que já estão rodando na máquina, por padrão os do docker-compose.local.yml do repositório BiniTech-PDV (RabbitMQ em 5672, Auth em 8081 e PDV em 8080).

No .env, PRODUCT_IMPORT_SERVICE_KEY, RABBITMQ_USERNAME e RABBITMQ_PASSWORD precisam ser iguais aos do .env.local do PDV. Não use $ nos valores: o Docker Compose interpreta $texto como variável.
