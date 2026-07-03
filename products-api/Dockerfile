# Etapa 1: Compilación del binario usando la versión exacta
FROM golang:1.26.4-alpine AS builder

WORKDIR /app

# Copiar descriptores primero para aprovechar la caché de Docker
COPY go.mod ./
RUN go mod download

# Copiar el código fuente y compilar de manera estática
COPY main.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o products-api .

# Etapa 2: Imagen limpia de ejecución
FROM alpine:3.18

WORKDIR /app

# Copiar únicamente el binario compilado desde la etapa previa
COPY --from=builder /app/products-api .

# Exponer el puerto por defecto de Go estipulado
EXPOSE 8081

# Ejecutar la API
CMD ["./products-api"]