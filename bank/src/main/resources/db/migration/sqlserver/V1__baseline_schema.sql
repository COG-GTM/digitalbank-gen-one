-- V1 baseline: Digital Bank schema as previously created by Hibernate ddl-auto=update (sqlserver).
-- Generated for org.hibernate.dialect.SQLServerDialect, the dialect configured in deploy/docker-compose/docker-compose-mssql.yml.
-- Existing databases are baselined at version 1 (spring.flyway.baseline-on-migrate) and skip this script.
-- Do not edit after release; add a new V<n>__<description>.sql for every schema change.

create table account (
    id numeric(19,0) not null,
    current_balance numeric(19,2),
    date_closed datetime,
    date_opened datetime,
    interest_rate double precision not null,
    name varchar(255),
    opening_balance numeric(19,2),
    payment_amount double precision not null,
    payment_due datetime,
    payment_term int not null,
    account_number numeric(19,0),
    account_standing_id numeric(19,0),
    account_type_id numeric(19,0),
    coowner_id numeric(19,0),
    owner_id numeric(19,0),
    ownership_type_id numeric(19,0),
    primary key (id)
);

create table account_number_seq (
    id numeric(19,0) not null,
    primary key (id)
);

create table account_standing (
    id numeric(19,0) not null,
    code varchar(255),
    name varchar(255),
    primary key (id)
);

create table account_transaction (
    id numeric(19,0) not null,
    amount numeric(19,2),
    description varchar(255),
    running_balance numeric(19,2),
    transaction_date datetime,
    account_id numeric(19,0),
    transaction_category_id numeric(19,0),
    transaction_number numeric(19,0),
    transaction_state_id numeric(19,0),
    transaction_type_id numeric(19,0),
    primary key (id)
);

create table account_type (
    id numeric(19,0) not null,
    category varchar(255),
    code varchar(255),
    interest_rate double precision not null,
    min_deposit numeric(19,2),
    name varchar(255),
    overdraft_fee numeric(19,2),
    overdraft_limit numeric(19,2),
    primary key (id)
);

create table acct_no_seq (
    next_val numeric(19,0)
);

insert into acct_no_seq values ( 486130001 );

create table credit_card_reference (
    id numeric(19,0) not null,
    account_open_date datetime,
    account_status varchar(255),
    application_date datetime,
    application_id numeric(19,0),
    application_number varchar(255),
    application_status varchar(255),
    application_status_detail varchar(1024),
    bank_user_id numeric(19,0),
    correlation_id varchar(255),
    credit_card_id numeric(19,0),
    credit_score varchar(255),
    credit_user_id numeric(19,0),
    risk_score varchar(255),
    primary key (id)
);

create table hibernate_sequence (
    next_val numeric(19,0)
);

insert into hibernate_sequence values ( 1 );

create table linked_account (
    id numeric(19,0) not null,
    account_balance varchar(255),
    account_id varchar(255),
    account_name varchar(255),
    account_number varchar(255),
    bank_id varchar(255),
    bank_name varchar(255),
    user_id numeric(19,0),
    primary key (id)
);

create table linked_user (
    id numeric(19,0) not null,
    password varchar(255),
    username varchar(255),
    user_id numeric(19,0),
    primary key (id)
);

create table message (
    message_id numeric(19,0) not null,
    content varchar(255),
    subject varchar(255),
    timestamp datetime,
    id numeric(19,0),
    primary key (message_id)
);

create table notification (
    id numeric(19,0) not null,
    content varchar(255),
    notification_type int,
    timestamp datetime,
    user_id numeric(19,0),
    primary key (id)
);

create table ownership_type (
    id numeric(19,0) not null,
    code varchar(255),
    name varchar(255),
    primary key (id)
);

create table role (
    id numeric(19,0) not null,
    name varchar(255),
    primary key (id)
);

create table trans_no_seq (
    next_val numeric(19,0)
);

insert into trans_no_seq values ( 845320001 );

create table transaction_category (
    id numeric(19,0) not null,
    code varchar(255),
    name varchar(255),
    primary key (id)
);

create table transaction_number_seq (
    id numeric(19,0) not null,
    primary key (id)
);

create table transaction_state (
    id numeric(19,0) not null,
    code varchar(255),
    name varchar(255),
    primary key (id)
);

create table transaction_type (
    id numeric(19,0) not null,
    category varchar(255),
    code varchar(255),
    name varchar(255),
    primary key (id)
);

create table user_profile (
    id numeric(19,0) not null,
    address varchar(255),
    country varchar(255),
    dob datetime not null,
    dom datetime,
    email_address varchar(255) not null,
    first_name varchar(255),
    gender varchar(255),
    home_phone varchar(255),
    last_name varchar(255),
    locality varchar(255),
    mobile_phone varchar(255),
    postal_code varchar(255),
    region varchar(255),
    ssn varchar(255) not null,
    title varchar(255),
    work_phone varchar(255),
    primary key (id)
);

create table user_role (
    id numeric(19,0) not null,
    role_id numeric(19,0),
    user_id numeric(19,0),
    primary key (id)
);

create table users (
    id numeric(19,0) not null,
    account_non_expired bit not null,
    account_non_locked bit not null,
    credentials_non_expired bit not null,
    enabled bit not null,
    password varchar(255),
    username varchar(255),
    profile_id numeric(19,0),
    primary key (id)
);

alter table user_profile
    add constraint UK_sqjkpr2tm75m7wc0e94mbi7r6 unique (email_address);

alter table user_profile
    add constraint UK_ffnvyvxfthcf4w6d1405b88a unique (ssn);

alter table account
    add constraint FKevlxau79fw5ptp2gtr6k1k6j6
    foreign key (account_number)
    references account_number_seq;

alter table account
    add constraint FK6sy0lidsmon6cyw39h4ss3ufc
    foreign key (account_standing_id)
    references account_standing;

alter table account
    add constraint FKgw84mgpacw9htdxcs2j1p7u6j
    foreign key (account_type_id)
    references account_type;

alter table account
    add constraint FKn42bac9pv34y9vj8oqhyy79r5
    foreign key (coowner_id)
    references users;

alter table account
    add constraint FKpmi9a540maunq440yp7jj2dg6
    foreign key (owner_id)
    references users;

alter table account
    add constraint FKrbcs952uw0leme6iwg23nqsm8
    foreign key (ownership_type_id)
    references ownership_type;

alter table account_transaction
    add constraint FKqonh25s0w6r5cf8jq88m6kd8o
    foreign key (account_id)
    references account;

alter table account_transaction
    add constraint FK9gufwvjs2ycffwgd0qdtvcuwb
    foreign key (transaction_category_id)
    references transaction_category;

alter table account_transaction
    add constraint FK5jv1nfphetg5ujci5w4cjl4ti
    foreign key (transaction_number)
    references transaction_number_seq;

alter table account_transaction
    add constraint FK21b1805d7j6tc2fjd10g51dq3
    foreign key (transaction_state_id)
    references transaction_state;

alter table account_transaction
    add constraint FK6yberemxcs5bqedy0vjdlu12i
    foreign key (transaction_type_id)
    references transaction_type;

alter table linked_account
    add constraint FKet4hjr1xmn73eblwu4kdn3ffk
    foreign key (user_id)
    references users;

alter table linked_user
    add constraint FK1co2pba3rv3xhgpcgg66dxv2h
    foreign key (user_id)
    references users;

alter table message
    add constraint FKadptf3bkbvdr8fbd2rl4mb96v
    foreign key (id)
    references users;

alter table notification
    add constraint FKnk4ftb5am9ubmkv1661h15ds9
    foreign key (user_id)
    references users;

alter table user_role
    add constraint FKa68196081fvovjhkek5m97n3y
    foreign key (role_id)
    references role;

alter table user_role
    add constraint FKj345gk1bovqvfame88rcx7yyx
    foreign key (user_id)
    references users;

alter table users
    add constraint FKo47sw84ajr24vfq4ccoha1256
    foreign key (profile_id)
    references user_profile;
