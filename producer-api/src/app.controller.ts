import { Controller, Post, Get, Inject, OnModuleInit, Query, Body, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { ClientKafka } from '@nestjs/microservices';
import { MongoClient, Collection } from 'mongodb';

interface KafkaItemInputDto {
  productId: string;
  quantity: number;
  unitPrice: number;
}

interface KafkaOrderInputDto {
  orderId: string;
  clientId: string;
  channel: string;
  createdAt?: string;
  items: KafkaItemInputDto[];
}

@Controller('orders')
export class AppController implements OnModuleInit {
  private readonly logger = new Logger(AppController.name);
  private mongoCollection!: Collection;

  constructor(
    @Inject('KAFKA_SERVICE') private readonly kafkaClient: ClientKafka,
  ) {}

  async onModuleInit() {
    await this.kafkaClient.connect();

    this.logger.log('✔ Cliente de Kafka inicializado en producer-api.');

    const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017';

    try {
      const mongoClient = await MongoClient.connect(mongoUri);
      
      const db = mongoClient.db('b2b_orders'); 

      this.mongoCollection = db.collection('enriched-orders'); 

      this.logger.log('Conexión establecida con MongoDB para auditoría en producer-api.');
    } catch (error) {
      this.logger.error('Error de conexión a MongoDB:', error);
    }
  }

  @Post('fire')
  fireTestOrder(@Body() body: KafkaOrderInputDto) {    
    if (!body.orderId || !body.clientId || !body.items || body.items.length === 0) {
      throw new BadRequestException('El payload de la orden está incompleto. Verifique los campos obligatorios.');
    }

    const orderPayload = {
      orderId: body.orderId,
      clientId: body.clientId,
      channel: body.channel || 'TRADICIONAL',
      createdAt: body.createdAt || new Date().toISOString(),
      items: body.items.map(item => ({
        productId: item.productId,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      })),
    };

    this.logger.log(`[producer-api] Publicando orden dinámica ${orderPayload.orderId} en el tópico...`);
    
    this.kafkaClient.emit('orders-topic', {
      key: orderPayload.orderId,
      value: orderPayload,
    });

    return {
      status: 'EVENT_EMITTED',
      message: `El evento de la orden ${orderPayload.orderId} fue inyectado con éxito en Kafka desde Postman.`,
      urlToVerify: `http://localhost:3001/orders/check?orderId=${orderPayload.orderId}`,
      orderId: orderPayload.orderId,
    };
  }

  @Get('check')
  async checkMongoOrder(@Query('orderId') orderId: string) {
    if (!orderId) {
      return { error: 'Debe proveer un parámetro ?orderId= en la URL.' };
    }

    this.logger.log(`[producer-api] Buscando la orden ${orderId} en MongoDB...`);
    
    const document = await this.mongoCollection.findOne({ orderId });

    if (!document) {
      throw new NotFoundException(
        `La orden ${orderId} aún no ha sido guardada en la colección por el Worker de Java.`,
      );
    }

    return {
      status: 'VERIFIED_IN_MONGODB',
      message: 'La orden fue procesada, enriquecida y persistida de forma correcta.',
      databaseValidation: {
        collection: 'enriched-orders',
        isChannelFieldOmitted: document.client && !('channel' in document.client), 
      },
      document, 
    };
  }
}