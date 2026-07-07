import { Module } from '@nestjs/common';
import { ClientsModule, Transport } from '@nestjs/microservices';
import { AppController } from './app.controller';

@Module({
  imports: [
    ClientsModule.register([
      {
        name: 'KAFKA_SERVICE',
        transport: Transport.KAFKA,
        options: {
          client: {
            clientId: 'nestjs-order-producer',
            brokers: [process.env.KAFKA_SERVERS || 'localhost:9092'],
          },
          consumer: {
            groupId: 'nestjs-producer-group',
          }
        },
      },
    ]),
  ],
  controllers: [AppController],
})
export class AppModule {}
