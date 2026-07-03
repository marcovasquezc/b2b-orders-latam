import { NestFactory } from '@nestjs/core';
import { ClientsModule } from './clients.module';

async function bootstrap() {
  const app = await NestFactory.create(ClientsModule);
    
  const port = process.env.PORT || 8082; 
  
  await app.listen(port);

  console.log(`Clients API (NestJS) escuchando activamente en el puerto ${port}`);
}

bootstrap();