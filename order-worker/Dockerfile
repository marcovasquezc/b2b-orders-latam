# Etapa 1: Compilación del binario utilizando una imagen ligera de Maven con Java 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .

# Descarga las dependencias en caché para acelerar construcciones futuras
RUN mvn dependency:go-offline -B
COPY src ./src

# Compila omitiendo los tests de integración (ya que no hay Docker ejecutándose dentro de Docker de forma nativa)
RUN mvn clean package -DskipTests

# Etapa 2: Construcción de la imagen final de ejecución ultra ligera
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/order-worker-0.0.1-SNAPSHOT.jar app.jar

# Exponemos el puerto por defecto
EXPOSE 8080

# Ejecutamos la aplicación habilitando optimizaciones nativas de la JVM para contenedores
ENTRYPOINT ["java", "-jar", "app.jar"]