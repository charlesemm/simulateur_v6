# Journal du projet

Une entree par brique terminee : date, fait, reste a faire.

2026-10-29 | Configuration initiale | pom.xml, application.yml, compose.yaml, Containerfile, .env.example, Flyway V1, test du socle | Reste : generer le wrapper Maven, lancer `podman compose up -d --build`, puis prompt 01 (verification du socle)
2026-10-30 | Prompt 01 - Socle | mvnw genere, artifactId flow-simulator, Lombok, V1 avec info_application, Containerfile en couches sur JRE alpine, mem_limit/cpus, .env local ; `clean verify` vert (3 tests), 3 conteneurs UP, /actuator/health = UP, flyway V1 | Reste : prompt 02 (referentiel synthetique)
