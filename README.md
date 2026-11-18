# My Stores API

Spring Boot 3 / Java 17 API with H2 storage, separate owner-seller and customer authentication, stores, products, orders, stock control, and catalog RAG with optional OpenAI generation.

## Source packages

- `com.mystores.controller` contains REST endpoints.
- `com.mystores.service` contains password reset and business services.
- `com.mystores.repository` contains Spring Data repositories.
- `com.mystores.entity` contains persisted JPA entities.
- `com.mystores.config` contains security, CORS, and owner bootstrap configuration.
- `com.mystores` contains the Spring Boot application entry point.

## Run locally

Set `OWNER_EMAIL` and `OWNER_PASSWORD` (at least 8 characters) before running `mvn spring-boot:run`. The first startup creates the single seller account from those values. Seller passwords and customer passwords are stored as BCrypt hashes; the original password cannot be recovered. Customers can register in the app. There is no seller registration route.

## Render environment

Set `OWNER_EMAIL` to your email address and `OWNER_PASSWORD` to the initial password before the first deployment of this version. The first API startup stores the seller email and BCrypt password hash in H2. Changing these environment values later will not overwrite the existing seller row; use the password reset flow to change its password.

For password reset emails, configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` and `MAIL_FROM`. For Gmail, use `smtp.gmail.com`, port `587`, and a Google App Password (not your normal Gmail password). Reset links expire after 15 minutes by default; `RESET_TOKEN_MINUTES` changes this. `RESET_BASE_URL` should be the frontend URL. Configure `FRONTEND_URL` for API CORS and optionally `OPENAI_API_KEY` / `OPENAI_MODEL`. The H2 database file is stored under `/app/data`; the Render manifest mounts a disk there.
