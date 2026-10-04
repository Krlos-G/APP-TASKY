# Imagem de producao do Tasky: o app Angular e a API num processo so.
#
# Os testes nao rodam aqui - quem roda e o CI, antes do deploy.
#
# Ensaio local:
#   docker build -t tasky .
#   docker run --rm -p 8090:8080 --network app-iphone_default --env-file .env \
#     -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/tasky tasky

# ---------------------------------------------------------------- front
FROM node:24-alpine AS front
WORKDIR /front

# Dependencias antes do codigo: mudar uma tela nao reinstala o node_modules.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# ---------------------------------------------------------------- back
FROM maven:3.9.16-eclipse-temurin-21 AS back
WORKDIR /back

COPY backend/pom.xml ./
RUN mvn -B --no-transfer-progress dependency:go-offline

COPY backend/src ./src
COPY --from=front /front/dist/frontend/browser/ ./src/main/resources/static/
RUN mvn -B --no-transfer-progress -Dmaven.test.skip=true package

# ------------------------------------------------------------- execucao
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S tasky && adduser -S tasky -G tasky
COPY --from=back /back/target/*.jar app.jar
USER tasky

# Sem teto, a JVM se dimensiona pela maquina da hospedagem - e a conta e
# cobrada por GB de memoria. A variavel pode ser trocada no painel sem rebuild.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx256m -Xss512k"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
