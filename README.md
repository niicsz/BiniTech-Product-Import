# BiniTech Product Import

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

## Variáveis

| Variável | Descrição |
|---|---|
| PRODUCT_IMPORT_MONGODB_URI | URI do MongoDB exclusivo deste serviço |
| PRODUCT_IMPORT_MONGODB_DATABASE | Base, padrão binitech_product_import |
| PRODUCT_IMPORT_SERVICE_KEY | Segredo compartilhado, mínimo 32 caracteres, para a API interna do PDV |
| AUTH_SERVICE_URL | URL do BiniTech Auth |
| PDV_SERVICE_URL | URL do backend BiniTech PDV |
| RABBITMQ_HOST, RABBITMQ_PORT | Broker já existente |
| RABBITMQ_USERNAME, RABBITMQ_PASSWORD | Credenciais do broker |
| CORS_ALLOWED_ORIGINS | Origens da SPA separadas por vírgula |
| PRODUCT_IMPORT_MAX_FILE_BYTES | Limite adicional, padrão 20 MiB |
| PRODUCT_IMPORT_MAX_RECORDS | Limite de linhas, padrão 50.000 |
| PRODUCT_IMPORT_BATCH_SIZE | Lote enviado ao PDV, padrão 250 e máximo 500 |

## Railway

O Mongo dedicado foi provisionado pelo Railway CLI no projeto steadfast-growth:

- serviço: MongoDB-jKw9
- service ID: 1c695309-d9d9-4f20-962a-2a9f95bb1fde
- volume exclusivo montado em /data/db

O serviço de aplicação também foi reservado, configurado sem deploy:

- serviço: BiniTech-Product-Import
- service ID: f609e353-0030-4a13-945b-37de8226e57f
- domínio: https://binitech-product-import-production.up.railway.app
- referências do Mongo, RabbitMQ, Auth e PDV configuradas com skip-deploys

Ao criar o serviço de aplicação com root directory product-import-service, use referências Railway, sem copiar segredos:

    PRODUCT_IMPORT_MONGODB_URI=${{MongoDB-jKw9.MONGO_URL}}
    RABBITMQ_HOST=${{rabbitmq.RAILWAY_PRIVATE_DOMAIN}}
    RABBITMQ_USERNAME=${{rabbitmq.RABBITMQ_DEFAULT_USER}}
    RABBITMQ_PASSWORD=${{rabbitmq.RABBITMQ_DEFAULT_PASS}}
    RABBITMQ_PORT=5672
    PDV_SERVICE_URL=http://${{BiniTech-PDV.RAILWAY_PRIVATE_DOMAIN}}:8080
    AUTH_SERVICE_URL=http://${{BiniTech-Auth.RAILWAY_PRIVATE_DOMAIN}}:8081

PRODUCT_IMPORT_SERVICE_KEY deve ter o mesmo valor no serviço de importação e no PDV. Não registre esse valor no repositório.

## Consistência e desempenho

- Arquivo e jobs ficam no banco exclusivo; produtos continuam pertencendo somente ao banco do PDV.
- Consultas por código de barras e gravações são em lote, sem N+1 e sem um save por produto.
- O consumer usa compare-and-set para um ImportJob não ser iniciado duas vezes.
- Cada comando recebe operationId igual a jobId:linha; recibos com índice único impedem repetição sequencial.
- Código de barras + tenant possui definição de índice único no PDV. Antes de criar o índice em base antiga, audite duplicidades.
- Sem transação distribuída entre bancos: falhas são registradas por linha, sem fingir atomicidade entre serviços.
- Estoque é ignorado por padrão. Substituir ou incrementar exige confirmação; valores anterior/importado ficam na auditoria.

O domínio legado do PDV ainda usa double para preço. O importador mantém dinheiro em BigDecimal/Mongo Decimal128 e só converte no boundary interno do PDV. A migração global de produtos, vendas e contratos públicos deve ser feita separadamente.
