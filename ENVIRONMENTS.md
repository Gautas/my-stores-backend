# Backend environments

Spring Boot profiles are `dev`, `staging`, `uat`, `preprod`, and `prod`.
If no profile is selected, local runs use `dev`.

Run locally from `backend`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
mvn spring-boot:run
```

For each hosted backend service set `SPRING_PROFILES_ACTIVE` and `APP_ENV` to
the same environment name. Set `FRONTEND_URL` to that environment's exact
frontend origin, and give each service its own `OWNER_EMAIL`, `OWNER_PASSWORD`,
`SESSION_SECRET`, `RESET_BASE_URL`, SMTP credentials, and `OPENAI_API_KEY` as
needed. Never reuse test owner credentials in production.

The profiles isolate H2 files and media paths under `./data/<profile>/` when
`DATABASE_URL` and `MEDIA_DIRECTORY` are not supplied. Each hosted environment
must have its own persistent disk mounted at `/app/data`; attaching one disk to
multiple services would mix or corrupt environment data. `DATABASE_URL` and
`MEDIA_DIRECTORY` can be set per service to override those paths.

The existing production Render services are not recreated by this change. To
provision dev, staging, UAT, and preprod, create a separate backend web service
for each profile and use a different frontend static site/API URL for each.
