# CommunityHub Backend

CommunityHub backend là API `Spring Boot` cho ứng dụng blog/forum dạng `post + comments`.

Stack chính:
- `Java 21`
- `Spring Boot 3`
- `PostgreSQL`
- `MinIO / S3-compatible storage`
- `JWT`
- `OIDC`

## Chức năng hiện có

### Auth
- đăng ký bằng username/password
- đăng nhập bằng username/password
- refresh token
- logout
- lấy current user qua `GET /api/auth/me`
- đăng nhập OIDC qua provider local/dev:
  - `google`
  - `facebook`

### Profile
- cập nhật username
- cập nhật avatar bằng `mediaKey`

### Media
- tạo media reservation
- upload ảnh trực tiếp qua presigned URL
- complete upload
- resolve read URL ngắn hạn cho ảnh

### Posts
- tạo post
- sửa post
- xóa post
- lấy feed posts theo cursor
- lấy post detail

### Comments
- tạo comment root
- tạo reply lồng nhiều cấp
- sửa comment
- xóa comment
- lấy root comments theo cursor
- lấy replies theo cursor

### Content state
- post/comment có `attachments_jsonb`
- post/comment có `editedAt`
- post/comment hỗ trợ soft delete
- post/comment có revision history nội bộ

## API Surface

### Auth
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/oauth/{provider}/start`
- `GET /api/auth/oauth/{provider}/callback`
- `POST /api/auth/oauth/exchange`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

### Profile
- `PATCH /api/profile`
- `POST /api/profile/avatar`

### Media
- `POST /api/media/reservations`
- `POST /api/media/{mediaKey}/complete`
- `POST /api/media/read-urls`

### Posts
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `DELETE /api/posts/{postId}`
- `GET /api/posts?sort=&cursor=&limit=`
- `GET /api/posts/{postId}`
- `GET /api/posts/{postId}/comments?parentId=&sort=&cursor=&limit=`

### Comments
- `POST /api/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

## Package Map

```text
com.app.communityhub
  auth/
    api/
    password/
    session/
    oauth/
    security/
  content/
    post/
    comment/
    shared/
  media/
    api/
    reservation/
    attachment/
    cleanup/
    storage/
  user/
    profile/
  common/
  config/
```

## Class UML

```mermaid
classDiagram
    class AuthController
    class PasswordAuthService
    class AuthSessionService
    class OAuthLoginFlowService
    class OAuthLoginTicketService
    class OidcClient
    class JwtService
    class CurrentUserService

    class ProfileController
    class ProfileService

    class MediaController
    class MediaReservationService
    class MediaAttachmentService
    class MediaCleanupService
    class ObjectStorageClient

    class PostController
    class PostService
    class CommentController
    class CommentService
    class ContentResponseAssembler
    class ContentRevisionRecorder
    class SnowflakeIdGenerator

    AuthController --> PasswordAuthService
    AuthController --> AuthSessionService
    AuthController --> OAuthLoginFlowService
    AuthController --> OAuthLoginTicketService
    AuthController --> CurrentUserService
    AuthController --> ProfileService

    PasswordAuthService --> AuthSessionService
    AuthSessionService --> JwtService
    OAuthLoginFlowService --> OidcClient
    OAuthLoginFlowService --> OAuthLoginTicketService

    ProfileController --> ProfileService
    ProfileController --> CurrentUserService

    MediaController --> MediaReservationService
    MediaController --> MediaAttachmentService
    MediaReservationService --> ObjectStorageClient
    MediaAttachmentService --> ObjectStorageClient
    MediaCleanupService --> ObjectStorageClient

    PostController --> PostService
    PostController --> CommentService
    CommentController --> CommentService
    PostService --> MediaAttachmentService
    PostService --> ContentResponseAssembler
    PostService --> ContentRevisionRecorder
    PostService --> SnowflakeIdGenerator
    CommentService --> MediaAttachmentService
    CommentService --> ContentResponseAssembler
    CommentService --> ContentRevisionRecorder
    CommentService --> SnowflakeIdGenerator
```

## Flow UML

### Password Auth

```mermaid
sequenceDiagram
    actor Client
    participant AuthController
    participant PasswordAuthService
    participant AuthSessionService
    participant JwtService

    Client->>AuthController: register / login
    AuthController->>PasswordAuthService: handle request
    PasswordAuthService->>AuthSessionService: issueTokensForUser
    AuthSessionService->>JwtService: generate access token
    AuthSessionService->>JwtService: generate refresh token
    AuthSessionService-->>Client: AuthResponse
```

### OAuth Login

```mermaid
sequenceDiagram
    actor Browser
    participant Frontend
    participant AuthController
    participant OAuthLoginFlowService
    participant OIDCProvider
    participant OAuthLoginTicketService
    participant AuthSessionService

    Browser->>Frontend: click provider button
    Frontend->>AuthController: GET /api/auth/oauth/{provider}/start
    AuthController->>OAuthLoginFlowService: start
    OAuthLoginFlowService-->>Browser: redirect to provider
    OIDCProvider-->>AuthController: callback with code/state
    AuthController->>OAuthLoginFlowService: complete
    OAuthLoginFlowService->>OAuthLoginTicketService: create ticket
    OAuthLoginFlowService-->>Browser: redirect back to frontend
    Frontend->>AuthController: POST /api/auth/oauth/exchange
    AuthController->>OAuthLoginTicketService: exchange
    OAuthLoginTicketService->>AuthSessionService: issueTokensForUser
    AuthSessionService-->>Frontend: AuthResponse
```

### Media Upload

```mermaid
sequenceDiagram
    actor Client
    participant MediaController
    participant MediaReservationService
    participant ObjectStorageClient
    participant S3 as MinIO/S3
    participant MediaAttachmentService

    Client->>MediaController: POST /api/media/reservations
    MediaController->>MediaReservationService: reserve
    MediaReservationService->>ObjectStorageClient: createUploadUrl
    MediaReservationService-->>Client: mediaKey + uploadUrl

    Client->>S3: PUT image
    Client->>MediaController: POST /api/media/{mediaKey}/complete
    MediaController->>MediaReservationService: complete
    MediaReservationService-->>Client: media metadata

    Client->>MediaController: POST /api/media/read-urls
    MediaController->>MediaAttachmentService: resolveReadUrls
    MediaAttachmentService-->>Client: read URLs
```

### Post + Comments Read

```mermaid
sequenceDiagram
    actor Client
    participant PostController
    participant PostService
    participant CommentService

    Client->>PostController: GET /api/posts
    PostController->>PostService: list(sort, cursor, limit)
    PostService-->>Client: CursorPageResponse<PostResponse>

    Client->>PostController: GET /api/posts/{postId}
    PostController->>PostService: get(postId)
    PostService-->>Client: PostResponse

    Client->>PostController: GET /api/posts/{postId}/comments
    PostController->>CommentService: getPage(postId, parentId, sort, cursor, limit)
    CommentService-->>Client: CursorPageResponse<CommentResponse>
```

## Database UML

```mermaid
erDiagram
    USERS {
        uuid id PK
        varchar username UK
        uuid avatar_media_id FK
        timestamptz created_at
        timestamptz updated_at
    }

    PASSWORD_CREDENTIALS {
        uuid user_id PK, FK
        varchar password_hash
        timestamptz created_at
        timestamptz updated_at
    }

    OAUTH_ACCOUNTS {
        uuid id PK
        uuid user_id FK
        varchar provider
        varchar provider_subject
        varchar email
        timestamptz created_at
        timestamptz updated_at
    }

    OAUTH_LOGIN_STATES {
        varchar state_hash PK
        varchar provider
        varchar code_verifier
        varchar nonce
        varchar return_to
        varchar redirect_uri
        timestamptz expires_at
        timestamptz consumed_at
        timestamptz created_at
    }

    OAUTH_LOGIN_TICKETS {
        varchar ticket_hash PK
        uuid user_id FK
        varchar return_to
        timestamptz expires_at
        timestamptz consumed_at
        timestamptz created_at
    }

    REFRESH_TOKENS {
        uuid id PK
        varchar token_id UK
        varchar token_hash UK
        uuid user_id FK
        boolean revoked
        timestamptz expires_at
        timestamptz created_at
        timestamptz revoked_at
    }

    MEDIA_ASSETS {
        uuid id PK
        varchar media_key UK
        varchar object_key UK
        uuid owner_user_id FK
        varchar status
        varchar mime_type
        bigint size_bytes
        int width
        int height
        varchar etag
        timestamptz reservation_expires_at
        timestamptz uploaded_at
        timestamptz attached_at
        timestamptz orphaned_at
        timestamptz created_at
        timestamptz updated_at
    }

    POSTS {
        bigint id PK
        uuid author_id FK
        varchar content
        jsonb attachments_jsonb
        timestamptz created_at
        timestamptz updated_at
        timestamptz edited_at
        timestamptz deleted_at
        uuid deleted_by_user_id FK
        varchar deleted_source
    }

    COMMENTS {
        bigint id PK
        bigint post_id FK
        uuid author_id FK
        bigint parent_id FK
        bigint root_id FK
        int depth
        varchar content
        jsonb attachments_jsonb
        timestamptz created_at
        timestamptz updated_at
        timestamptz edited_at
        timestamptz deleted_at
        uuid deleted_by_user_id FK
        varchar deleted_source
    }

    POST_REVISIONS {
        uuid id PK
        bigint entity_id
        int revision_number
        varchar event_type
        varchar action_source
        varchar content
        jsonb attachments_jsonb
        uuid actor_user_id FK
        timestamptz created_at
    }

    COMMENT_REVISIONS {
        uuid id PK
        bigint entity_id
        bigint post_id
        bigint parent_id
        bigint root_id
        int depth
        int revision_number
        varchar event_type
        varchar action_source
        varchar content
        jsonb attachments_jsonb
        uuid actor_user_id FK
        timestamptz created_at
    }

    USERS ||--|| PASSWORD_CREDENTIALS : owns
    USERS ||--o{ OAUTH_ACCOUNTS : links
    USERS ||--o{ REFRESH_TOKENS : receives
    USERS ||--o{ MEDIA_ASSETS : owns
    USERS ||--o{ POSTS : writes
    USERS ||--o{ COMMENTS : writes
    USERS ||--o{ OAUTH_LOGIN_TICKETS : receives
    POSTS ||--o{ COMMENTS : contains
    POSTS ||--o{ POST_REVISIONS : snapshots
    COMMENTS ||--o{ COMMENT_REVISIONS : snapshots
    COMMENTS o|--o{ COMMENTS : parent_child
```

## Cấu hình `app.*`

Backend dùng `AppProperties` để gom cấu hình riêng của ứng dụng.

### `app.cors`
- `allowedOrigins`

### `app.security.jwt`
- `issuer`
- `secret`
- `accessTokenTtl`
- `refreshTokenTtl`

### `app.oauth`
- `frontendCallbackUri`
- `stateTtl`
- `ticketTtl`
- `providers.google.*`
- `providers.facebook.*`

Mỗi provider có:
- `enabled`
- `issuerUri`
- `clientId`
- `clientSecret`
- `redirectUri`

### `app.media`
- `bucket`
- `endpoint`
- `accessKey`
- `secretKey`
- `region`
- `pathStyleAccessEnabled`
- `uploadUrlTtl`
- `readUrlTtl`
- `reservationTtl`
- `orphanRetention`
- `maxFileSizeBytes`
- `allowedMimeTypes`

### `app.content.posts`
- `maxAttachments`
- `defaultPageSize`
- `maxPageSize`

### `app.content.comments`
- `maxAttachments`
- `defaultPageSize`
- `maxPageSize`

### `app.ids`
- `workerId`

## Cursor Pagination

### Feed
- route: `GET /api/posts`
- sort hỗ trợ:
  - `newest`
  - `oldest`
- response:

```json
{
  "items": [],
  "nextCursor": "opaque-or-null",
  "hasMore": true,
  "sort": "newest"
}
```

### Comments
- route: `GET /api/posts/{postId}/comments`
- root comments nhận:
  - `sort`
  - `cursor`
  - `limit`
- replies nhận:
  - `parentId`
  - `cursor`
  - `limit`

### Cursor token
- cursor là opaque token
- payload logic bám theo:
  - `id`
  - `sort`
  - `parentId`

### Sort behavior
- posts:
  - `newest`
  - `oldest`
- root comments:
  - `newest`
  - `oldest`
- replies:
  - trả theo nhánh reply của `parentId`


## Testing

```bash
./gradlew test
```
