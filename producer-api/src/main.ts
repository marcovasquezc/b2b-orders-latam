import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { MicroserviceOptions, Transport } from '@nestjs/microservices';
import { Logger } from '@nestjs/common';

async function bootstrap() {
  const logger = new Logger('Bootstrap');
  const app = await NestFactory.create(AppModule);

  const port = process.env.PORT || 3001;
  
  app.connectMicroservice<MicroserviceOptions>({
    transport: Transport.KAFKA,
    options: {
      client: {
        clientId: 'nestjs-order-producer',
        brokers: [process.env.KAFKA_SERVERS || 'localhost:9092'],
      },
      consumer: {
        groupId: 'nestjs-producer-group',
      },
    },
  });

  await app.startAllMicroservices();
    
  await app.listen(port);
  
  logger.log('Microservicio producer-api corriendo en: http://localhost:3001');
}

bootstrap();
