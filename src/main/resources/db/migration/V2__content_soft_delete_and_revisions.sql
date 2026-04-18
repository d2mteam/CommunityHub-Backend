alter table posts
    add column edited_at timestamp with time zone null,
    add column deleted_at timestamp with time zone null,
    add column deleted_by_user_id uuid null,
    add column deleted_source varchar(20) null;

alter table posts
    add constraint fk_posts_deleted_by_user
    foreign key (deleted_by_user_id) references users(id);

alter table comments
    add column edited_at timestamp with time zone null,
    add column deleted_at timestamp with time zone null,
    add column deleted_by_user_id uuid null,
    add column deleted_source varchar(20) null;

alter table comments
    add constraint fk_comments_deleted_by_user
    foreign key (deleted_by_user_id) references users(id);

create table post_revisions (
    id uuid primary key,
    entity_id bigint not null,
    revision_number integer not null,
    event_type varchar(20) not null,
    action_source varchar(20) not null,
    content varchar(5000) not null,
    attachments_jsonb jsonb not null default '[]'::jsonb,
    actor_user_id uuid not null references users(id),
    created_at timestamp with time zone not null,
    constraint uk_post_revisions_entity_revision unique (entity_id, revision_number)
);

create table comment_revisions (
    id uuid primary key,
    entity_id bigint not null,
    post_id bigint not null,
    parent_id bigint null,
    root_id bigint null,
    depth integer not null,
    revision_number integer not null,
    event_type varchar(20) not null,
    action_source varchar(20) not null,
    content varchar(5000) not null,
    attachments_jsonb jsonb not null default '[]'::jsonb,
    actor_user_id uuid not null references users(id),
    created_at timestamp with time zone not null,
    constraint uk_comment_revisions_entity_revision unique (entity_id, revision_number)
);

create index idx_posts_deleted_at_id on posts(deleted_at, id);
create index idx_comments_deleted_at_id on comments(deleted_at, id);
create index idx_post_revisions_entity_id_created_at on post_revisions(entity_id, created_at);
create index idx_comment_revisions_entity_id_created_at on comment_revisions(entity_id, created_at);
