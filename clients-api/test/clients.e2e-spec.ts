import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { ClientsModule } from './../src/clients.module';

describe('ClientsController (e2e)', () => {
  let app: INestApplication;

  beforeEach(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [ClientsModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    await app.init();
  });

  it('/clients/CLI-001 (GET) - Debe retornar el cliente Alicorp', () => {
    return request(app.getHttpServer())
      .get('/clients/CLI-001')
      .expect(200)
      .expect((res) => {
        expect(res.body.clientId).toEqual('CLI-001');
        expect(res.body.name).toContain('Alicorp');
      });
  });

  afterAll(async () => {
    await app.close();
  });
});
