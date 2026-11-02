# Simulateur de flux CNAM

Simulateur de flux de donnees synthetiques (prestations, puis affiliation et recouvrement).
Spring Boot 3, Java 21, PostgreSQL 16, Podman. Voir `CLAUDE.md` (regles) et `docs/00-cahier-des-charges.md`.

## Demarrage (Windows, PowerShell dans IntelliJ)

```powershell
# 1. Une seule fois : generer le wrapper Maven
#    IntelliJ > panneau Maven > Execute Maven Goal > wrapper:wrapper

# 2. Secrets
Copy-Item .env.example .env        # puis editer .env

# 3. Conteneurs
podman machine start               # si la machine Podman est arretee
podman compose up -d --build
podman compose ps

# 4. Verifier
curl.exe http://localhost:8080/simulateur/actuator/health     # {"status":"UP"}

# 5. Tests (Testcontainers)
.\mvnw.cmd verify
```

Application : http://localhost:8080/simulateur/
Documentation de l'API : http://localhost:8080/simulateur/swagger-ui.html

## Arret

```powershell
podman compose down        # garde la base
podman compose down -v     # efface aussi la base
```

## Observabilite (optionnel)

```powershell
podman compose --profile observability up -d     # Grafana sur http://localhost:3000
```
