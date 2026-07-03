# Etapa 1: Construcción y compilación de TypeScript usando la versión exacta
FROM node:24-alpine AS builder

WORKDIR /app

# Copiar descriptores primero para aprovechar la caché de capas de Docker
COPY package*.json ./
RUN npm install

# Copiar todo el código fuente y compilar a producción
COPY . .
RUN npm run build

# Etapa 2: Imagen limpia y optimizada para ejecución en producción
FROM node:24-alpine

WORKDIR /app

# Variable de entorno de Node optimizada para producción
ENV NODE_ENV=production

# Instalar únicamente dependencias necesarias para ejecución (descarta devDependencies)
COPY package*.json ./
RUN npm install --only=production

# Copiar exclusivamente los archivos JavaScript compilados desde la etapa previa
COPY --from=builder /app/dist ./dist

# Exponer el puerto por defecto
EXPOSE 8082

# Ejecutar el servidor usando el script de producción estándar
CMD ["npm", "run", "start:prod"]