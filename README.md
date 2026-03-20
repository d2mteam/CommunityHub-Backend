# CommunityHub Backend

Backend MVP 1 dung `Spring Boot + PostgreSQL + MinIO`.

## Da co
- auth: register, login, refresh, logout, me
- profile update
- avatar attach bang `mediaKey`
- media reservation, complete, read URL resolution
- post va comment dang cay
- root-first comment pagination

## Nguyen tac implementation
- Controller layer lay current user va truyen `userId` vao service.
- Service layer khong doc JWT hay `SecurityContextHolder`.
- `MediaService` chi quan ly media lifecycle:
  - `RESERVED`
  - `UPLOADED`
  - `ATTACHED`
  - `ORPHANED`
- Domain service khac tu attach media vao avatar/post/comment.

## Chay local

Tu workspace root:

```bash
./scripts/start-local-stack.sh
```

Sau do:

```bash
./gradlew bootRun
```

Hoac chay full stack tu workspace root:

```bash
./scripts/start-dev-stack.sh
```

Mac dinh backend dung:
- PostgreSQL: `localhost:5432`
- MinIO API: `localhost:9100`
- MinIO console: `localhost:9101`
- CORS frontend origin: `http://localhost:5173`

## Test

```bash
./gradlew test
```

## File tham chieu
- [../MEDIA_PRESIGNED_URL_FLOW.md](../MEDIA_PRESIGNED_URL_FLOW.md)
- [../COMMENT_THREAD_LOADING_FLOW.md](../COMMENT_THREAD_LOADING_FLOW.md)
