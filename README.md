# Mini Autorizador

[![CI](https://github.com/dedezza1D/mini-authorizer/actions/workflows/ci.yml/badge.svg)](https://github.com/dedezza1D/mini-authorizer/actions/workflows/ci.yml)

API REST que emite cartões benefício, informa o saldo e autoriza transações, como um autorizador
que decide se uma compra feita na maquininha de cartão é aprovada.

- Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Liquibase, MySQL 5.7
- Testes: JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers, JaCoCo e PIT (testes de mutação)

## Sumário

- [Como executar](#como-executar)
- [API](#api)
- [Design](#design)
- [Segurança](#segurança)
- [Autenticação dos clientes](#autenticação-dos-clientes)
- [Concorrência](#concorrência)
- [Idempotência](#idempotência)
- [Testes](#testes)
- [Suposições](#suposições)
- [Rumo à produção](#rumo-à-produção)

## Como executar

Requisitos: JDK 21 e Docker. Não é preciso instalar o Maven: o wrapper baixa a versão certa.

```bash
docker compose -f docker/docker-compose.yml up -d   # MySQL 5.7 em localhost:3306
./mvnw spring-boot:run                              # API em http://localhost:8080
```

O schema é criado pelo Liquibase na subida, e o usuário da API `username` / `password` é criado se
ainda não existir. Tudo pode ser alterado por variáveis de ambiente:

| Variável | Padrão local | Uso |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/miniautorizador?sslMode=REQUIRED&...` | URL JDBC (sempre com TLS) |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / vazia | Credenciais do banco |
| `API_USERNAME` / `API_PASSWORD` | `username` / `password` | Usuário da API criado na subida |
| `PIN_PEPPER` | valor público, só para uso local | Segredo (Base64, 32+ bytes) das senhas dos cartões |
| `TOKEN_SIGNING_KEY` / `TOKEN_VERIFICATION_KEY` | chave efêmera | Par EC P-256 (PEM) que assina os tokens OAuth2 |
| `TLS_CERTIFICATE` / `TLS_PRIVATE_KEY` | `certs/server.crt` / `certs/server.key` | Certificado PEM do perfil `tls` |
| `TLS_CLIENT_CA` | `certs/client-ca.crt` | CA dos certificados de cliente (mTLS) |

Com `SPRING_PROFILES_ACTIVE=prod` não há valores padrão: a aplicação não sobe enquanto banco,
usuário da API, `PIN_PEPPER` e as chaves dos tokens não forem informados.

### HTTPS

O contrato chama `http://localhost:8080`, então o perfil padrão continua em HTTP. Para a aplicação
servir HTTPS na 8443:

```bash
./scripts/generate-dev-certificate.sh                     # servidor, CA de clientes e client.p12 em certs/
SPRING_PROFILES_ACTIVE=tls ./mvnw spring-boot:run          # API em https://localhost:8443
curl --cacert certs/server.crt -u username:password https://localhost:8443/cartoes/6549873025634501
```

Com mTLS, o certificado do cliente substitui a senha:

```bash
curl --cacert certs/server.crt --cert-type P12 --cert certs/client.p12:changeit https://localhost:8443/cartoes/6549873025634501
```

### Contêiner

A imagem roda com um usuário sem privilégios:

```bash
docker build -t mini-authorizer .
docker run --rm -p 8080:8080 -e DB_URL="jdbc:mysql://host.docker.internal:3306/miniautorizador?sslMode=REQUIRED&tlsVersions=TLSv1.2&serverTimezone=UTC" mini-authorizer
```

### Testes

Com o Docker rodando, porque o Testcontainers sobe o próprio MySQL 5.7:

```bash
./mvnw verify                                                 # todos os testes + limite de cobertura
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage  # testes de mutação
```

Relatórios: `target/site/jacoco/index.html` e `target/pit-reports/index.html`.

## API

Todos os endpoints exigem autenticação: HTTP Basic, como pede o contrato, um token OAuth2 ou um
certificado de cliente (veja [Autenticação dos clientes](#autenticação-dos-clientes)). Caminhos,
nomes de campos e códigos de recusa fazem parte do contrato público e seguem em português; o
código-fonte é em inglês. Um cliente que passa do seu limite de requisições recebe `429`.

### Criar cartão

```bash
curl -i -u username:password -H 'Content-Type: application/json' \
     -d '{"numeroCartao": "6549873025634501", "senha": "1234"}' \
     http://localhost:8080/cartoes
```

| Resultado | Status | Corpo |
|---|---|---|
| Criado, com R$ 500,00 | `201` | `{"senha": "1234", "numeroCartao": "6549873025634501"}` |
| Cartão já existe | `422` | o mesmo corpo acima |
| Dados inválidos | `400` | problem details com cada campo inválido |
| Sem autenticação | `401` | vazio |

### Consultar saldo

```bash
curl -i -u username:password http://localhost:8080/cartoes/6549873025634501
```

| Resultado | Status | Corpo |
|---|---|---|
| Encontrado | `200` | `495.15` |
| Cartão inexistente | `404` | vazio |
| Sem autenticação | `401` | vazio |

### Autorizar transação

```bash
curl -i -u username:password -H 'Content-Type: application/json' \
     -H 'Idempotency-Key: nsu-000123' \
     -d '{"numeroCartao": "6549873025634501", "senhaCartao": "1234", "valor": 10.00}' \
     http://localhost:8080/transacoes
```

O header `Idempotency-Key` é opcional (veja [Idempotência](#idempotência)).

| Resultado | Status | Corpo (text/plain) |
|---|---|---|
| Autorizada e debitada | `201` | `OK` |
| Cartão inexistente | `422` | `CARTAO_INEXISTENTE` |
| Cartão bloqueado por senhas erradas | `422` | `CARTAO_BLOQUEADO` |
| Senha errada | `422` | `SENHA_INVALIDA` |
| Saldo insuficiente | `422` | `SALDO_INSUFICIENTE` |
| Dados ou `Idempotency-Key` inválidos | `400` | problem details |
| `Idempotency-Key` já usada em outra transação | `409` | problem details |
| Sem autenticação | `401` | vazio |

### Obter token OAuth2

```bash
curl -s -u username:password -d grant_type=client_credentials http://localhost:8080/oauth2/token
```

```json
{"access_token": "eyJhbGciOiJFUzI1NiJ9...", "token_type": "Bearer", "expires_in": 300, "scope": "api"}
```

O token vai no header `Authorization: Bearer ...` de qualquer endpoint acima. Um pedido inválido
recebe `400` com os erros do RFC 6749 (`unsupported_grant_type`, `invalid_scope`, `invalid_request`).

## Design

O código é organizado por funcionalidade (`card`, `transaction`, `security`), e não por camada:
cada pacote reúne tudo sobre um conceito e expõe só o que os outros precisam.

```
card/         entidade Card, emissão, consulta de saldo, débito atômico, hashing da senha
transaction/  regras de autorização, autorizador, idempotência, endpoint
security/     usuários da API, Basic, tokens OAuth2, mTLS, bloqueio por tentativas, rate limit
web/          problem details para erros de validação
```

**Regras de autorização como estratégias (Strategy).** Cada regra implementa `AuthorizationRule`:
diz se permite a transação e qual recusa produz caso contrário. As regras são beans do Spring
aplicados na ordem de `@Order` pelo `TransactionAuthorizer`. Uma regra nova (limite diário,
categoria do estabelecimento) é uma classe nova, sem alterar o autorizador (princípio
aberto/fechado). O bloqueio do cartão por senha errada entrou exatamente assim: uma classe a mais.

```java
public interface AuthorizationRule {
    boolean allows(Card card, Transaction transaction);
    RejectionReason rejectionReason();

    default void onAllowed(Card card, Transaction transaction) { }
    default void onRejected(Card card, Transaction transaction) { }

    default void check(Card card, Transaction transaction) {
        Optional.of(card)
                .filter(candidate -> allows(candidate, transaction))
                .ifPresentOrElse(allowed -> onAllowed(allowed, transaction), () -> {
                    onRejected(card, transaction);
                    throw new TransactionRejectedException(rejectionReason());
                });
    }
}
```

Os ganchos `onAllowed` e `onRejected` deixam cada regra reagir ao próprio resultado: a regra de
senha conta o erro antes de recusar e esquece os erros quando a senha está certa.

A ordem das regras é cartão bloqueado, senha e saldo. Um cartão bloqueado é recusado sem conferir
a senha, então palpites durante o bloqueio não contam nem revelam nada; e uma senha errada num
cartão zerado responde `SENHA_INVALIDA`, então sem a senha certa ninguém descobre o saldo.

**Nenhum `if`.** O desafio opcional foi cumprido em todo o `src/main`. As decisões são expressas
com polimorfismo (as regras), `Optional` (`orElseThrow`, `filter`, `map`, `ifPresentOrElse`) e mapas
de enum para resultado (resultado da emissão para status HTTP, decisão do rate limit), sem ternários
disfarçados. A recusa
é uma exceção de domínio convertida no `422` em um único lugar.

**Domínio rico, controllers finos.** O `Card` conhece o saldo inicial, sabe se comporta um valor e
se uma senha confere, sem nunca expor o hash. Os controllers só traduzem entre HTTP e domínio. Os
records de requisição e resposta usam os nomes de campo do contrato; o domínio usa nomes em inglês.

**Portas para o que muda com o ambiente.** O hashing da senha do cartão fica atrás da interface
`PinHasher`: hoje é software com pepper, numa bandeira real seria um HSM, sem mudar o domínio.

**Erros.** Falhas de validação respondem com problem details
([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) e um mapa `errors` por campo. Número do cartão
e senha de uma *transação* só têm presença e tamanho validados: cartão inexistente e senha errada
precisam receber o código do contrato, e não um `400`.

**Persistência.** MySQL 5.7, como fornecido no `docker-compose.yml`, o que definiu parte da stack:

- O `MySQLDialect` do Hibernate 6.6 só suporta MySQL 8+, então é usado o `MySQLLegacyDialect` da
  comunidade. É também por isso que o projeto fica no Spring Boot 3.5: o Boot 4 traz o Hibernate 7.
- O Flyway Community não suporta mais o MySQL 5.7, então as migrations são em Liquibase
  (changesets em SQL puro).

O `ddl-auto` é `validate`: o Liquibase é dono do schema, e o Hibernate só confere se ele bate com
as entidades.

## Segurança

**Transporte.**

- *API:* o perfil `tls` serve HTTPS aceitando só TLS 1.3 e 1.2, e no 1.2 apenas suítes com forward
  secrecy e criptografia autenticada (ECDHE + GCM/ChaCha20). O certificado PEM é recarregado sem
  restart quando renovado (cert-manager, certbot). No perfil `prod`, o TLS é terminado no load
  balancer: os headers `X-Forwarded-*` passam a valer, a requisição é vista como HTTPS e o Spring
  Security envia HSTS.
- *Banco:* a conexão com o MySQL exige TLS (`sslMode=REQUIRED`, TLS 1.2, o máximo do 5.7): sem TLS
  no servidor, a conexão falha em vez de cair para texto puro. Em produção, `VERIFY_IDENTITY` com a
  CA do banco valida também o certificado do servidor.

**Usuários da API.**

- **No banco** (`api_user`), com hash BCrypt atrás de um `DelegatingPasswordEncoder`
  (`{bcrypt}...`): o algoritmo ou o custo podem mudar depois sem invalidar os hashes gravados.
  Usuários podem ser desabilitados.
- **Stateless:** sem sessão HTTP, sem cookie, sem request cache. A proteção CSRF fica desligada
  porque não existe credencial ambiente (cookie) que uma requisição de outro site possa usar.
- **`401` uniforme:** usuário inexistente, senha errada, conta desabilitada ou bloqueada recebem o
  mesmo `401` com `WWW-Authenticate: Basic realm="mini-authorizer"` e corpo vazio, sem revelar
  quais usuários existem.
- **Bloqueio contra força bruta:** 5 senhas erradas seguidas bloqueiam o usuário por 15 minutos
  (ambos configuráveis). O contador é atualizado com `UPDATE`s condicionais, então é exato entre
  instâncias; tentativas durante o bloqueio não contam e não o estendem.
- **Nenhuma credencial no código:** o usuário inicial vem de variáveis de ambiente, a senha nunca
  vai para o log, um usuário existente nunca é sobrescrito e o perfil `prod` não tem padrões.
- Só `/actuator/health` e `/actuator/info` são públicos, para o health check do balanceador.

**Senhas dos cartões.** Uma senha de 4 dígitos tem só 10.000 valores possíveis: com o hash BCrypt de
um dump do banco, a busca offline leva minutos. Por isso o BCrypt é aplicado sobre um HMAC-SHA256
do número do cartão e da senha, com uma chave secreta (*pepper*) que nunca vai para o banco.

- Sem o pepper, que vem de `PIN_PEPPER` (um gerenciador de segredos em produção), o dump do banco
  não basta para testar senhas.
- O número do cartão entra no HMAC, então um hash copiado de um cartão não valida em outro.
- O pepper precisa ter pelo menos 32 bytes; o perfil `prod` não sobe sem ele.
- Bandeiras reais vão além: validam a senha num HSM e nunca a veem em texto puro. A interface
  `PinHasher` é o ponto onde essa implementação entraria.

O pepper fecha o ataque offline, mas um cliente da API ainda poderia tentar as 10.000 senhas
online. Por isso **3 senhas erradas dentro de 30 minutos bloqueiam o cartão por 30 minutos**
(configuráveis), com a resposta `CARTAO_BLOQUEADO`:

- erros mais velhos que a janela não contam, e depois do bloqueio a contagem recomeça do zero;
- uma senha certa esquece os erros anteriores, sem escrever nada quando não havia erros;
- janela, contador e bloqueio são `UPDATE`s condicionais: o limite vale entre instâncias e
  tentativas simultâneas são todas contadas;
- a senha errada é contada mesmo com a transação recusada, inclusive no fluxo idempotente.

**Logs.** O número do cartão aparece mascarado (`654987******4501`), e o `Transaction.toString()`
nunca inclui a senha.

## Autenticação dos clientes

O Basic manda a senha em toda requisição e custa uma checagem BCrypt (dezenas de milissegundos de
CPU) por chamada. Ele continua aceito, porque o contrato o exige, mas há dois caminhos melhores
para comunicação entre sistemas:

| Mecanismo | Como o cliente prova quem é | Custo por requisição |
|---|---|---|
| HTTP Basic | usuário e senha em toda requisição | BCrypt + consulta ao banco |
| OAuth2 client credentials | senha uma vez em `POST /oauth2/token`; depois um JWT de 5 minutos | só a verificação da assinatura ES256 |
| mTLS (perfil `tls`) | certificado assinado pela CA de clientes, verificado no handshake | nenhum além do handshake |

**OAuth2 client credentials** ([RFC 6749, seção 4.4](https://www.rfc-editor.org/rfc/rfc6749#section-4.4)):

- O token é um JWT ES256 com emissor, audiência, validade de 5 minutos, id único e escopo `api`.
  A API confere assinatura, emissor, audiência e validade; um token inválido recebe o desafio do
  RFC 6750 dizendo por quê.
- Só a credencial do próprio cliente obtém token, nunca outro token, e a resposta proíbe cache.
- As chaves vêm de PEM compartilhado entre as instâncias (`TOKEN_SIGNING_KEY`); localmente, sem
  chave, é gerada uma efêmera.
- Com `app.security.tokens.enabled=false` o emissor embutido sai de cena e a API passa a validar
  tokens de um provedor externo (Keycloak, Cognito) via
  `spring.security.oauth2.resourceserver.jwt.issuer-uri`, que é o desenho mais comum em produção.
- Uma requisição com token não grava nada no banco: o evento de login só zera as tentativas falhas
  em login por senha.

**mTLS:** no perfil `tls`, o cliente pode se autenticar só com o certificado. O CN é o usuário da
API, que ainda precisa estar habilitado e desbloqueado. `client-auth: want` mantém os clientes
Basic e Bearer funcionando, então o mTLS pode ser adotado cliente a cliente.

**Limite de requisições por cliente.** Cada cliente autenticado (por qualquer dos três meios) tem um
token bucket: rajada de 200 e 100 requisições por segundo sustentadas, configuráveis. Passou
disso, `429` com `Retry-After`. Os buckets ficam na memória de cada instância, então o limite
efetivo cresce com o número de instâncias: é uma rede de proteção, e o limite global por cliente é
papel do API gateway, que vê o tráfego de todas.

## Concorrência

> Um cartão tem R$ 10,00. Duas transações de R$ 10,00 chegam ao mesmo tempo, em instâncias
> diferentes da aplicação.

O débito é um único comando condicional:

```sql
update card set balance = balance - :amount, version = version + 1
 where card_number = :number and balance >= :amount
```

O InnoDB trava a linha durante o update, então débitos simultâneos no mesmo cartão são executados
um depois do outro, e a condição é avaliada sobre o último saldo confirmado. Uma transação altera
a linha; a outra não altera nada e é recusada com `SALDO_INSUFICIENTE`. Isso vale para qualquer
número de instâncias, porque quem serializa é o banco e não a JVM, e dispensa lock distribuído.

As regras continuam rodando antes, sobre o cartão lido, para recusar senha errada e valores que
claramente não cabem sem tomar lock.

O histórico de commits mostra como se chegou aqui. A primeira versão carregava o cartão, subtraía e
salvava. Um teste disparando 60 transações simultâneas de R$ 10,00 num cartão com R$ 500,00
aprovou só **6**: as outras 54 falharam com exceção de lock otimista (um `500` para a maquininha),
embora o saldo cobrisse 50. Com o update condicional, o mesmo teste aprova exatamente 50, recusa 10
e o saldo termina em 0,00.

A criação de cartão tem o mesmo tipo de corrida (duas requisições para o mesmo número novo): a
chave primária decide, e a requisição perdedora recebe o `422` de cartão existente.

## Idempotência

Maquininhas reenviam a transação quando a resposta não chega a tempo. Sem proteção, cada reenvio é
um novo débito. O terminal pode mandar o identificador da transação (o NSU) no header
`Idempotency-Key`; sem o header, o fluxo do contrato original não muda.

A resposta fica gravada em `processed_transaction`, com chave primária (cliente da API, chave),
**na mesma transação de banco do débito**:

| Situação | Comportamento |
|---|---|
| Reenvio depois que a primeira tentativa terminou | Encontra o registro e devolve a mesma resposta, inclusive uma recusa, sem rodar as regras nem debitar |
| Reenvio enquanto a primeira tentativa ainda roda | Falha na chave primária ao gravar o registro, o que desfaz o próprio débito, e responde com o que a primeira gravou |
| Mesma chave com outro cartão ou valor | `409`: não é um reenvio, é outra transação |

As chaves são separadas por cliente da API (duas adquirentes podem ter a mesma numeração), a senha
não é gravada, e um job remove registros com mais de 7 dias, já que reenvios acontecem em minutos.

Um teste dispara 20 reenvios simultâneos com o mesmo NSU: todos recebem `OK`, e o cartão é
debitado uma vez só.

## Testes

São 198 testes, divididos assim:

| Tipo | O que garante | Exemplos |
|---|---|---|
| Unidade | Lógica de domínio e cada caminho dos serviços, com BCrypt e HMAC reais em vez de hash simulado | `CardTest`, `PepperedPinHasherTest`, `TransactionAuthorizerTest`, `IdempotentTransactionAuthorizerUnitTest` |
| Fatia web | O contrato exato: status, corpos em texto, eco do JSON, erros de validação, `401`, `409` | `CardControllerTest`, `TransactionControllerTest` |
| Repositório | Queries e constraints contra o MySQL 5.7 real (Testcontainers), conexão cifrada | `CardRepositoryTest`, `ProcessedTransactionRepositoryTest`, `DatabaseTlsTest` |
| Concorrência | Transações e reenvios simultâneos em conexões separadas nunca debitam a mais | `ConcurrentAuthorizationTest`, `IdempotentTransactionAuthorizerTest` |
| Bloqueio do cartão | Janela, contagem e bloqueio em SQL real, e o fluxo completo pelas regras | `CardPinLockoutRepositoryTest`, `PinLockoutTest` |
| Segurança | Filtros reais: Basic, OAuth2 (token expirado, de outro emissor, de outra audiência), mTLS, bloqueios, rate limit; handshakes TLS de verdade | `ApiSecurityTest`, `OAuth2ClientCredentialsTest`, `TlsTest`, `RateLimitTest` |
| Aceitação | O roteiro de avaliação via HTTP, da criação do cartão a cada recusa, reenvios com NSU e bloqueio | `ContractAcceptanceTest` |

**Cobertura:** 99% das linhas e 100% dos branches (JaCoCo); o build falha abaixo de 95%. As linhas
fora tratam algoritmos indisponíveis (HmacSHA256, curva P-256), que nenhum JDK padrão produz.

**Testes de mutação.** Cobertura só diz que o código rodou. O PIT altera o código de produção
(inverte condições, troca retornos, remove chamadas) e confere se algum teste falha a cada
alteração. Resultado atual: **178 de 178 mutantes mortos (100%)**, e o build falha abaixo de 90%.

O PIT achou fraquezas reais que a cobertura alta escondia:

- o teste do controller comparava o corpo da resposta com `reason.code()`, o próprio método
  testado, então trocar todos os códigos de recusa por uma string vazia continuava passando. Agora
  o teste verifica os códigos do contrato literalmente;
- o fluxo de idempotência só era exercitado por testes de integração, que ficam fora do PIT;
  ganhou testes unitários que provam, por exemplo, o rollback do débito no reenvio concorrente;
- um teste de configuração aceitava um `TokenIssuer` nulo (`hasSingleBean` conta beans nulos);
  agora emite e valida um token de verdade;
- o arredondamento do `Retry-After` podia ser trocado sem nenhum teste falhar, e com razão: o
  bucket repõe ao menos uma requisição por segundo, então a espera nunca passa de 1 s. A conta saiu.

Os testes também pegaram erros de configuração antes de qualquer revisão: com SSL bundle, o Spring
Boot ignora `server.ssl.ciphers` (o servidor aceitava cifras CBC); um `delete` em massa deixava
entidades apagadas no cache do JPA; e o `NimbusJwtDecoder.withPublicKey` só aceita RSA, então o
decoder ES256 é montado direto sobre o processador do Nimbus.

As classes de configuração do Spring ficam fora do PIT: seus métodos `@Bean` rodam uma vez na
subida do contexto de teste, que o PIT reaproveita entre mutantes, então mutá-los não prova nada.
Os testes de integração cobrem essas classes.

## Suposições

- **Número do cartão:** 13 a 19 dígitos (faixa da ISO/IEC 7812). **Senha do cartão:** 4 a 6 dígitos.
- **Valor:** positivo, no mínimo 0,01, com no máximo duas casas decimais; fora disso é `400`.
- **Formato do saldo:** sempre com duas casas decimais (`500.00`, `495.15`).
- **Cartão repetido:** o cartão existente não é alterado, qualquer que seja a senha da nova requisição.
- **Precedência das recusas:** cartão inexistente, cartão bloqueado, senha errada, saldo insuficiente.
- **`CARTAO_BLOQUEADO`** é um código além dos três do contrato. O roteiro de avaliação erra a senha
  uma vez só, então nunca chega a bloquear um cartão.
- **Bloqueio do cartão:** a terceira senha errada dentro da janela ainda responde `SENHA_INVALIDA` e
  bloqueia o cartão; a partir da tentativa seguinte, a resposta é `CARTAO_BLOQUEADO`.
- **Requisições malformadas** (JSON inválido, campos ausentes) recebem `400`, caso que o contrato
  não cobre.
- **Transações não são persistidas**, como a especificação permite; só o saldo do cartão muda. A
  exceção é o registro de idempotência, que guarda só a resposta, por alguns dias.
- **Reenvio com a mesma chave e outra senha** recebe a resposta original: a chave identifica a
  transação, e uma nova tentativa do portador deve vir com um novo NSU.
- **Usuários da API** são gerenciados no banco; o usuário inicial existe para um ambiente novo
  funcionar sem passos manuais.

## Rumo à produção

O que esta versão ainda deixa de fora, e como seria resolvido:

- **Rotação do pepper.** Trocar o pepper invalida os hashes existentes. Guardar o identificador da
  chave junto do hash permitiria conviver com duas versões durante a migração.
- **Revogação.** Um token emitido vale até expirar (5 minutos), mesmo que o cliente seja
  desabilitado; certificados de cliente não têm checagem de revogação (CRL/OCSP). Tokens e
  certificados de vida curta limitam a janela; uma lista de tokens revogados por `jti` a fecharia.
- **Rate limit global.** O limite por instância é uma rede de proteção; o global por cliente fica
  no API gateway (ou num bucket distribuído, como Bucket4j sobre Redis).
- **Efeito colateral dos bloqueios.** Quem conhece um nome de usuário ou um número de cartão pode
  bloqueá-lo de propósito. A duração curta limita o dano; rate limit no gateway e alertas nos
  eventos de bloqueio fechariam a brecha.
- **Observabilidade.** Métricas por motivo de recusa e um log de auditoria das transações (quem,
  quando, qual regra) ficariam ao lado do autorizador.
