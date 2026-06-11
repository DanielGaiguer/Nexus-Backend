# Nexus — Intelligent Matching Platform

> Trabalho de Conclusão de Curso (TCC) — Desenvolvimento de Sistemas

Nexus é uma plataforma web de intermediação entre empresas e profissionais autônomos de TI, desenvolvida como Trabalho de Conclusão de Curso. O sistema funciona como um **hub inteligente de oportunidades**, conectando empresas que buscam profissionais para projetos com freelancers qualificados, através de um motor de matchmaking algorítmico que calcula automaticamente a compatibilidade entre perfis.

A ideia central é simples: em vez de buscas manuais, o sistema recomenda profissionais para empresas (e vice-versa) com base em um score de compatibilidade, funcionando de forma semelhante a um **Tinder corporativo** — ambos os lados precisam demonstrar interesse para que um match seja confirmado e os contatos liberados.

---

## 💡 Como funciona

1. A empresa cadastra um projeto com requisitos, orçamento e skills necessárias
2. O sistema calcula automaticamente a compatibilidade de todos os profissionais cadastrados com aquele projeto
3. A empresa visualiza um ranking e demonstra interesse nos profissionais mais compatíveis
4. O profissional recebe o convite e pode aceitar ou recusar
5. Se ambos os lados concordam, o **match é confirmado** e os contatos são liberados

O score de compatibilidade é calculado pela fórmula:

```
Score = (Skills × 0.35) + (Orçamento × 0.25) + (Histórico × 0.20) + (Reputação × 0.10) + (Disponibilidade × 0.10)
```

---

## 🏗️ Tecnologias utilizadas

**Backend**
- Java 21
- Spring Boot
- Spring Security
- JPA / Hibernate
- JWT (JSON Web Tokens)
- BCrypt

**Banco de dados**
- MySQL

**Arquitetura**
- REST API
- Camadas: Controller → Service → Repository → Model

---

## 👥 Perfis de usuário

| Perfil | Descrição |
|---|---|
| `PROFESSIONAL` | Profissional autônomo que busca projetos |
| `COMPANY` | Empresa que cadastra projetos e busca profissionais |
| `ADMIN` | Administrador da plataforma — aprova empresas e gerencia o sistema |

> Empresas passam por uma validação do administrador antes de acessar a plataforma.

---

## 📄 Documentação da API

A documentação completa de todos os endpoints, parâmetros, corpos de requisição e resposta está disponível no arquivo abaixo:

**[📥 nexus-api-documentation.docx](https://github.com/DanielGaiguer/Nexus-Backend/blob/master/nexus-api-documentation.docx)**

---

## 🚧 Status do projeto

Este projeto está em desenvolvimento ativo como TCC. O backend está sendo construído e documentado progressivamente.

- [x] Modelagem do banco de dados
- [x] Entidades e enums
- [x] Repositories
- [x] Services (incluindo motor de matchmaking)
- [x] Autenticação JWT
- [x] Controllers REST
- [x] Documentação da API
- [ ] Frontend
- [ ] Testes
- [ ] Deploy

---

## ⚙️ Como executar localmente

**Pré-requisitos:** Java 21, Maven, MySQL

```bash
# Clone o repositório
git clone https://github.com/DanielGaiguer/Nexus-Backend.git

# Configure o banco de dados em src/main/resources/application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/db_nexus
spring.datasource.username=seu_usuario
spring.datasource.password=sua_senha

# Execute o projeto
mvn spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

---

## 👨‍💻 Autor

Desenvolvido por **Daniel Gaiguer** como Trabalho de Conclusão de Curso.
