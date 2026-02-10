create table users (
    id uuid primary key,
    username varchar(50) not null unique,
    password_hash varchar(255) not null,
    avatar_media_id uuid null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table refresh_tokens (
    id uuid primary key,
    token_id varchar(100) not null unique,
    token_hash varchar(255) not null unique,
    user_id uuid not null references users(id),
    revoked boolean not null default false,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    revoked_at timestamp with time zone null
);

create table media_assets (
    id uuid primary key,
    media_key varchar(100) not null unique,
    object_key varchar(255) not null unique,
    owner_user_id uuid not null references users(id),
    status varchar(20) not null,
    mime_type varchar(100) not null,
    size_bytes bigint not null,
    width integer null,
    height integer null,
    etag varchar(100) null,
    reservation_expires_at timestamp with time zone not null,
    uploaded_at timestamp with time zone null,
    attached_at timestamp with time zone null,
    orphaned_at timestamp with time zone null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

alter table users
    add constraint fk_users_avatar_media
    foreign key (avatar_media_id) references media_assets(id);

create table posts (
    id uuid primary key,
    author_id uuid not null references users(id),
    content varchar(5000) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table post_attachments (
    id uuid primary key,
    post_id uuid not null references posts(id) on delete cascade,
    media_asset_id uuid not null references media_assets(id),
    order_index integer not null
);

create table comments (
    id uuid primary key,
    post_id uuid not null references posts(id) on delete cascade,
    author_id uuid not null references users(id),
    parent_id uuid null references comments(id) on delete cascade,
    root_id uuid null references comments(id) on delete cascade,
    depth integer not null,
    content varchar(5000) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table comment_attachments (
    id uuid primary key,
    comment_id uuid not null references comments(id) on delete cascade,
    media_asset_id uuid not null references media_assets(id),
    order_index integer not null
);

create index idx_refresh_tokens_user_id on refresh_tokens(user_id);
create index idx_media_assets_owner_user_id on media_assets(owner_user_id);
create index idx_media_assets_status_updated_at on media_assets(status, updated_at);
create index idx_posts_author_id on posts(author_id);
create index idx_comments_post_id_created_at on comments(post_id, created_at);
