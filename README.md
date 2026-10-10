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
- [Concorrência](#concorrência)
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

| Variável | Padrão | Uso |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/miniautorizador?...` | URL JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / vazia | Credenciais do banco |
| `API_USERNAME` / `API_PASSWORD` | `username` / `password` | Usuário da API criado na subida |

Com `SPRING_PROFILES_ACTIVE=prod` não há valores padrão: a aplicação não sobe enquanto todas as
variáveis acima não forem informadas.

Para rodar como contêiner (a imagem roda com um usuário sem privilégios):

```bash
docker build -t mini-authorizer .
docker run --rm -p 8080:8080 -e DB_URL="jdbc:mysql://host.docker.internal:3306/miniautorizador?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" mini-authorizer
```

Testes (com o Docker rodando, porque o Testcontainers sobe o próprio MySQL 5.7):

```bash
./mvnw verify                                                 # todos os testes + limite de cobertura
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage  # testes de mutação
```

Relatórios: `target/site/jacoco/index.html` e `target/pit-reports/index.html`.

## API

Todos os endpoints exigem autenticação HTTP Basic. Caminhos, nomes de campos e códigos de recusa
fazem parte do contrato público e seguem em português; o código-fonte é em inglês.

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
     -d '{"numeroCartao": "6549873025634501", "senhaCartao": "1234", "valor": 10.00}' \
     http://localhost:8080/transacoes
```

| Resultado | Status | Corpo (text/plain) |
|---|---|---|
| Autorizada e debitada | `201` | `OK` |
| Cartão inexistente | `422` | `CARTAO_INEXISTENTE` |
| Senha errada | `422` | `SENHA_INVALIDA` |
| Saldo insuficiente | `422` | `SALDO_INSUFICIENTE` |
| Dados inválidos | `400` | problem details com cada campo inválido |
| Sem autenticação | `401` | vazio |

## Design

O código é organizado por funcionalidade (`card`, `transaction`, `security`), e não por camada:
cada pacote reúne tudo sobre um conceito e expõe só o que os outros precisam.

```
card/         entidade Card, emissão, consulta de saldo, débito atômico
transaction/  regras de autorização, serviço autorizador, endpoint
security/     usuários da API, autenticação Basic, bloqueio por tentativas
web/          problem details para erros de validação
```

**Regras de autorização como estratégias (Strategy).** Cada regra implementa `AuthorizationRule`:
diz se permite a transação e qual recusa produz caso contrário. As regras são beans do Spring
aplicados na ordem de `@Order` pelo `TransactionAuthorizer`. Uma regra nova (limite diário, cartão
bloqueado, categoria do estabelecimento) é uma classe nova, sem alterar o autorizador
(princípio aberto/fechado).

```java
public interface AuthorizationRule {
    boolean allows(Card card, Transaction transaction);
    RejectionReason rejectionReason();

    default void check(Card card, Transaction transaction) {
        Optional.of(card)
                .filter(candidate -> allows(candidate, transaction))
                .orElseThrow(() -> new TransactionRejectedException(rejectionReason()));
    }
}
```

A regra de senha roda antes da de saldo, então uma senha errada num cartão zerado responde
`SENHA_INVALIDA`: sem a senha certa, quem chama não descobre nada sobre o saldo.

**Nenhum `if`.** O desafio opcional foi cumprido em todo o `src/main`. As decisões são expressas
com polimorfismo (as regras), `Optional` (`orElseThrow`, `filter`, `map`) e mapas de enum para
resultado (resultado da emissão para status HTTP), sem ternários disfarçados. A recusa é uma
exceção de domínio convertida no `422` em um único lugar.

**Domínio rico, controllers finos.** O `Card` conhece o saldo inicial, sabe se comporta um valor e
se uma senha confere com o hash, sem nunca expor o hash. Os controllers só traduzem entre HTTP e
domínio. Os records de requisição e resposta usam os nomes de campo do contrato; o domínio usa
nomes em inglês.

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

O contrato exige HTTP Basic, então o trabalho foi deixar o Basic seguro para rodar:

- **Usuários da API no banco** (`api_user`), com hash BCrypt atrás de um
  `DelegatingPasswordEncoder` (`{bcrypt}...`): o algoritmo ou o custo podem mudar depois sem
  invalidar os hashes gravados. Usuários podem ser desabilitados.
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
- **Senhas dos cartões com hash** BCrypt, nunca em texto puro.
- **Número do cartão mascarado nos logs** (`654987******4501`), e o `Transaction.toString()` nunca
  inclui a senha.
- Só `/actuator/health` e `/actuator/info` são públicos, para o health check do balanceador.

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

## Testes

São 88 testes, divididos assim:

| Tipo | O que garante | Exemplos |
|---|---|---|
| Unidade | Lógica de domínio e cada caminho dos serviços, com BCrypt real em vez de hash simulado | `CardTest`, `AuthorizationRulesTest`, `TransactionAuthorizerTest`, `LoginAttemptsTest` |
| Fatia web | O contrato exato: status, corpos em texto, eco do JSON, erros de validação, `401` | `CardControllerTest`, `TransactionControllerTest` |
| Repositório | Queries e constraints contra o MySQL 5.7 real (Testcontainers) | `CardRepositoryTest`, `ApiUserRepositoryTest` |
| Concorrência | Transações simultâneas em conexões separadas nunca deixam o saldo negativo | `ConcurrentAuthorizationTest` |
| Segurança | Filtros reais: Basic, usuário desabilitado, bloqueio e expiração, sem sessão | `ApiSecurityTest` |
| Aceitação | O roteiro de avaliação via HTTP, da criação do cartão a cada recusa | `ContractAcceptanceTest` |

**Cobertura:** 100% de linhas e branches (JaCoCo). O `./mvnw verify` falha abaixo de 95%.

**Testes de mutação.** Cobertura só diz que o código rodou. O PIT altera o código de produção
(inverte condições, troca retornos, remove chamadas) e confere se algum teste falha a cada
alteração. Resultado atual: **69 de 69 mutantes mortos (100%)**, e o build falha abaixo de 90%.

A primeira rodada do PIT achou fraquezas reais que os 100% de cobertura escondiam. A mais clara: o
teste do controller comparava o corpo da resposta com `reason.code()`, o próprio método testado,
então trocar todos os códigos de recusa por uma string vazia continuava passando. Agora o teste
verifica os códigos do contrato literalmente.

As classes de configuração do Spring ficam fora do PIT: seus métodos `@Bean` rodam uma vez na
subida do contexto de teste, que o PIT reaproveita entre mutantes, então mutá-los não prova nada.
Os testes de integração cobrem essas classes.

## Suposições

- **Número do cartão:** 13 a 19 dígitos (faixa da ISO/IEC 7812). **Senha do cartão:** 4 a 6 dígitos.
- **Valor:** positivo, no mínimo 0,01, com no máximo duas casas decimais; fora disso é `400`.
- **Formato do saldo:** sempre com duas casas decimais (`500.00`, `495.15`).
- **Cartão repetido:** o cartão existente não é alterado, qualquer que seja a senha da nova requisição.
- **Precedência das recusas:** cartão inexistente, depois senha errada, depois saldo insuficiente.
- **Requisições malformadas** (JSON inválido, campos ausentes) recebem `400`, caso que o contrato
  não cobre.
- **Transações não são persistidas**, como a especificação permite; só o saldo do cartão muda.
- **Usuários da API** são gerenciados no banco; o usuário inicial existe para um ambiente novo
  funcionar sem passos manuais.

## Rumo à produção

O que esta versão deixa de fora de propósito, e como seria resolvido:

- **Idempotência.** Maquininhas reenviam a transação em caso de timeout, e um reenvio é debitado
  duas vezes. Um identificador da transação vindo do terminal (NSU), gravado com constraint única
  na mesma transação de banco do débito, tornaria os reenvios seguros.
- **Senhas dos cartões.** Uma senha de 4 dígitos tem só 10.000 valores possíveis, então um hash
  vazado pode ser quebrado offline mesmo com BCrypt. Bandeiras reais validam a senha num HSM e nunca
  a veem em texto puro.
- **Autenticação dos clientes.** O Basic envia a credencial em toda requisição e custa uma checagem
  BCrypt por chamada. Entre sistemas, OAuth2 client credentials (tokens de vida curta) ou mTLS se
  encaixam melhor, com TLS terminado no gateway, que também deve limitar a taxa por cliente.
- **Efeito colateral do bloqueio.** Quem conhece um nome de usuário pode bloqueá-lo de propósito. A
  duração curta limita o dano; rate limit no gateway e alertas nos eventos de bloqueio fechariam a
  brecha.
- **Observabilidade.** Métricas por motivo de recusa e um log de auditoria das transações (quem,
  quando, qual regra) ficariam ao lado do autorizador.
