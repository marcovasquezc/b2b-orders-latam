import { Injectable, NotFoundException } from '@nestjs/common';

export interface Client {
  clientId: string;
  name: string;
  segment: 'MAYORISTA' | 'MINORISTA';
  taxRegime: 'RESPONSABLE_IVA' | 'NO_RESPONSABLE';
  region: string;
  channel: string;
}

@Injectable()
export class ClientsService {  
  private readonly clients: Record<string, Client> = {
    'CLI-001': { clientId: 'CLI-001', name: 'Alicorp Mayorista S.A.', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Lima Metropolitana', channel: 'TRADICIONAL' },
    'CLI-002': { clientId: 'CLI-002', name: 'Comercializadora del Sur E.I.R.L.', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Arequipa', channel: 'TRADICIONAL' },
    'CLI-003': { clientId: 'CLI-003', name: 'Bodega Don Carlos', segment: 'MINORISTA', taxRegime: 'NO_RESPONSABLE', region: 'Trujillo', channel: 'TRADICIONAL' },
    'CLI-004': { clientId: 'CLI-004', name: 'Supermercados Peruanos', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Piura', channel: 'MODERNO' },
    'CLI-005': { clientId: 'CLI-005', name: 'Minimarket La Economía', segment: 'MINORISTA', taxRegime: 'NO_RESPONSABLE', region: 'Cusco', channel: 'TRADICIONAL' },
    'CLI-006': { clientId: 'CLI-006', name: 'Corporación Vega Almacenes S.A.C.', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Callao', channel: 'MODERNO' },
    'CLI-007': { clientId: 'CLI-007', name: 'Inversiones Alva & Hijos', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Chiclayo', channel: 'TRADICIONAL' },
    'CLI-008': { clientId: 'CLI-008', name: 'Tienda Mass Jesus María', segment: 'MINORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Lima', channel: 'MODERNO' },
    'CLI-009': { clientId: 'CLI-009', name: 'Comercial Selva Central', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Iquitos', channel: 'TRADICIONAL' },
    'CLI-010': { clientId: 'CLI-010', name: 'Bodega Santa Rosa', segment: 'MINORISTA', taxRegime: 'NO_RESPONSABLE', region: 'Puno', channel: 'DIGITAL' }
  };

  findOne(clientId: string): Client {
    const client = this.clients[clientId];
    
    if (!client) {
      throw new NotFoundException({ error: 'Cliente no encontrado' });
    }
    
    return client;
  }
}