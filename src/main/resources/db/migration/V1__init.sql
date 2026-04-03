create table users (
    id uuid primary key,
    username varchar(50) not null unique,
    avatar_media_id uuid null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table password_credentials (
    user_id uuid primary key references users(id) on delete cascade,
    password_hash varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table oauth_accounts (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    provider varchar(50) not null,
    provider_subject varchar(255) not null,
    email varchar(255) null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_oauth_accounts_provider_subject unique (provider, provider_subject)
);

create table oauth_login_states (
    state_hash varchar(64) primary key,
    provider varchar(50) not null,
    code_verifier varchar(128) not null,
    nonce varchar(128) not null,
    return_to varchar(500) not null,
    redirect_uri varchar(500) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone null,
    created_at timestamp with time zone not null
);

create table oauth_login_tickets (
    ticket_hash varchar(64) primary key,
    user_id uuid not null references users(id) on delete cascade,
    return_to varchar(500) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone null,
    created_at timestamp with time zone not null
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
    id bigint primary key,
    author_id uuid not null references users(id),
    content varchar(5000) not null,
    attachments_jsonb jsonb not null default '[]'::jsonb,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table comments (
    id bigint primary key,
    post_id bigint not null references posts(id) on delete cascade,
    author_id uuid not null references users(id),
    parent_id bigint null references comments(id) on delete cascade,
    root_id bigint null references comments(id) on delete cascade,
    depth integer not null,
    content varchar(5000) not null,
    attachments_jsonb jsonb not null default '[]'::jsonb,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_refresh_tokens_user_id on refresh_tokens(user_id);
create index idx_oauth_accounts_user_id on oauth_accounts(user_id);
create index idx_oauth_login_states_expires_at on oauth_login_states(expires_at);
create index idx_oauth_login_tickets_expires_at on oauth_login_tickets(expires_at);
create index idx_media_assets_owner_user_id on media_assets(owner_user_id);
create index idx_media_assets_status_updated_at on media_assets(status, updated_at);
create index idx_posts_author_id on posts(author_id);
create index idx_comments_post_parent_id_id on comments(post_id, parent_id, id);
