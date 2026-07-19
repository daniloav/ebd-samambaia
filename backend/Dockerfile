# ---------- Estágio 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# baixa dependências primeiro (cache de camada)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# As chaves JWT não são versionadas: gera se não vierem no contexto de build.
RUN if [ ! -f src/main/resources/privateKey.pem ]; then \
      (command -v openssl >/dev/null 2>&1 || (apt-get update && apt-get install -y openssl)) && \
      openssl genrsa -out src/main/resources/privateKey.pem 2048 && \
      openssl rsa -in src/main/resources/privateKey.pem -pubout -out src/main/resources/publicKey.pem ; \
    fi
RUN mvn -q -B package -DskipTests

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/target/quarkus-app/*.jar ./
COPY --from=build /app/target/quarkus-app/app/ ./app/
COPY --from=build /app/target/quarkus-app/quarkus/ ./quarkus/
# Roda como usuário não-root (boa prática de segurança)
RUN groupadd --system --gid 1001 appuser && useradd --system --uid 1001 --gid appuser appuser
USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
