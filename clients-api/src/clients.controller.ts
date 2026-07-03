import { Controller, Get, Param } from '@nestjs/common';
import { ClientsService, type Client } from './clients.service';

@Controller('clients')
export class ClientsController {  
  constructor(private readonly clientsService: ClientsService) {}

  @Get(':clientId')
  getClientById(@Param('clientId') clientId: string): Client {
    return this.clientsService.findOne(clientId);
  }
}