# Running promo-x

How to run promo-x on your machine. The shared infrastructure, Keycloak, comes first; each service then has its
own guide.

Prerequisites: JDK 25 and Docker.

## 1. Start Keycloak

Keycloak issues the JWTs that promo-x checks. It runs on http://localhost:8080 (admin / admin), and its compose
file uses an external network:

```bash
docker network create promo-x-network
docker compose -f common-infra/dev/docker-compose.dev.yaml up -d
```

Create a realm with your clients and roles, and a mapper that puts the realm roles in a top-level `roles` claim.

## 2. Run the services

- [promo-x-core](promo-x-core/run.md): its database and Redis, migrations, environment variables and profiles,
  and its logs, including how to read them in Docker, Elastic Beanstalk and Kubernetes.
- [promo-x-dashboard](promo-x-dashboard/run.md): the staff web app. Its Keycloak client, configuration and build.
  Start promo-x-core first.
