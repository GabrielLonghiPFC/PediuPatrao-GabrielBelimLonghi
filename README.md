# Pediu Patrão

Atividade prática da M1.

**Stack:** Java 21, Spring Boot, Spring Security, MongoDB, Thymeleaf.

---

## Como rodar

**Pré-requisitos:** JDK 21, Maven, MongoDB rodando em `localhost:27017`.

1. Clone o repositório e entre na pasta do projeto.
2. Confira a conexão no `application.properties`:
   - `spring.data.mongodb.uri=mongodb://127.0.0.1:27017/pizzaria`
3. Compile e suba a aplicação:

```bash
mvn clean install
mvn spring-boot:run
```

4. Acesse no navegador: **http://localhost:8080**

### Usuários

Na primeira execução é criado o **admin** (se ainda não existir):

| Usuário | Senha     | Perfil |
|---------|-----------|--------|
| admin   | teste123  | ADMIN  |

**Gerente** e **atendente** você cadastra pelo menu **Usuários** (logado como admin), com perfil `GERENTE` ou `ATENDENTE`.

---

## Requisitos implementados

| RF   | Descrição                                      | Status |
|------|------------------------------------------------|--------|
| RF01 | Três perfis, permissões no backend e APIs      | OK     |
| RF02 | Pedido com itens, total no servidor            | OK     |
| RF03 | Fluxo de status, entrada/saída e responsáveis  | OK     |
| RF04 | Desconto (máx. 20%) e cancelamento (gerente)   | OK     |
| RF05 | Auditoria persistente e consulta admin/gerente | OK     |

---

## Principais alterações

- **Perfis** `ADMIN`, `GERENTE` e `ATENDENTE` no login e no `SecurityConfig` (rotas web e API, resposta 403 na API).
- **Pedido:** itens, cálculo de preço/total no servidor, desconto, datas e responsáveis de entrada/saída, fluxo de status validado.
- **RF04:** desconto só gerente (até 20%, antes da saída); cancelamento com justificativa.
- **Auditoria:** coleção `auditoria` no MongoDB; registros de pedidos (inclusão, status, desconto, cancelamento) e clientes (inclusão/alteração); tela **Auditoria** para admin e gerente.
- **Validações** de pedido em `validations/PedidoValidacao.java`.
- Telas de pedidos (lista, novo, detalhe) e ajustes de menu conforme perfil (Thymeleaf + `sec:authorize`).

---

## Rotas úteis (web)

| Rota        | Quem usa                          |
|-------------|-----------------------------------|
| `/pedidos`  | Admin, gerente, atendente         |
| `/clientes` | Gerente (consulta), atendente (CRUD) |
| `/usuarios` | Admin                             |
| `/auditoria`| Admin, gerente                    |

APIs REST em `/api/pedidos`, `/api/clientes`, `/api/auditoria` (mesmas regras de perfil no backend).

---

**Autor:** Gabriel Belim Longhi

---

## Observação sobre o histórico do Git

Não sei quem é Janaina esse nome aparece em commits antigos do histórico porque já estavam no .git do projeto base disponibilizado. O trabalho da atividade foi feito por mim em cima do código principal, honestamente nem conheço essa pessoa. Não refiz o projeto deletando a pasta .git antes porquê quando percebi já estava no meio do projeto, com algumas partes commitadas. 
