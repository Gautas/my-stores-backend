# My Stores API

Spring Boot 3 / Java 17 API with H2 storage, separate owner-seller and customer authentication, stores, products, orders, stock control, and catalog RAG with optional OpenAI generation.

## Run locally

Set `OWNER_PASSWORD` to a local development password if desired, then run `gradle bootRun`. The API listens on port 8080. The default seller email is `owner@mystores.local` and the development password is `change-me-now`.

## Render environment

Set `OWNER_EMAIL`, a strong `OWNER_PASSWORD`, `FRONTEND_URL` (the deployed static site origin), and optionally `OPENAI_API_KEY` / `OPENAI_MODEL`. The H2 database file is stored under `/app/data`; the Render manifest mounts a disk there.
