package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

type Product struct {
	ProductID     string `json:"productId"`
	Name          string `json:"name"`
	SKU           string `json:"sku"`
	Category      string `json:"category"`
	TaxCategory   string `json:"taxCategory"` // GRAVADO (19%), REDUCIDO (5%), EXENTO (0%)
	UnitOfMeasure string `json:"unitOfMeasure"`
}

var productsCatalog = map[string]Product{
	// --- GRAVADO (19%) ---
	"PROD-001": {ProductID: "PROD-001", Name: "Gaseosa Inca Kola 3L", SKU: "GAS-INK-3L", Category: "Bebidas azucaradas", TaxCategory: "GRAVADO", UnitOfMeasure: "BOTELLA"},
	"PROD-002": {ProductID: "PROD-002", Name: "Bebida de Naranja Gloria 1L", SKU: "BEB-NAR-1L", Category: "Bebidas azucaradas", TaxCategory: "GRAVADO", UnitOfMeasure: "UNIDAD"},
	"PROD-003": {ProductID: "PROD-003", Name: "Chocolate Sublime Clásico 26g", SKU: "SUB-CLA-26G", Category: "Confitería", TaxCategory: "GRAVADO", UnitOfMeasure: "UNIDAD"},
	"PROD-004": {ProductID: "PROD-004", Name: "Caramelos de Limón Ambrosoli Bolsa 1kg", SKU: "CAR-LIM-AMB", Category: "Confitería", TaxCategory: "GRAVADO", UnitOfMeasure: "BOLSA"},
	"PROD-005": {ProductID: "PROD-005", Name: "Detergente Bolívar Azul 2kg", SKU: "BOL-AZU-2K", Category: "Productos de aseo", TaxCategory: "GRAVADO", UnitOfMeasure: "UNIDAD"},
	"PROD-006": {ProductID: "PROD-006", Name: "Jabón Moncler 145g", SKU: "JAB-MON-145G", Category: "Productos de aseo", TaxCategory: "GRAVADO", UnitOfMeasure: "UNIDAD"},
	"PROD-007": {ProductID: "PROD-007", Name: "Fertilizante Urea Molimax 5kg", SKU: "MOL-URE-5K", Category: "Insumos agrícolas", TaxCategory: "GRAVADO", UnitOfMeasure: "SACO"},
	"PROD-008": {ProductID: "PROD-008", Name: "Semillas de Papa Canchán Selecta 2kg", SKU: "SEM-PAP-CAN", Category: "Insumos agrícolas", TaxCategory: "GRAVADO", UnitOfMeasure: "SACO"},

	// --- REDUCIDO (5%) ---
	"PROD-009": {ProductID: "PROD-009", Name: "Aceite de Cocina Primor Premium 1L", SKU: "PRI-PRE-1L", Category: "Canasta familiar", TaxCategory: "REDUCIDO", UnitOfMeasure: "BOTELLA"},
	"PROD-010": {ProductID: "PROD-010", Name: "Mantequilla Laive con Sal 250g", SKU: "LAI-SAL-250", Category: "Canasta familiar", TaxCategory: "REDUCIDO", UnitOfMeasure: "UNIDAD"},
	"PROD-011": {ProductID: "PROD-011", Name: "Atun Primor Premium 100gr", SKU: "ATU-PRIM-100", Category: "Canasta familiar", TaxCategory: "REDUCIDO", UnitOfMeasure: "UNIDAD"},
	"PROD-012": {ProductID: "PROD-012", Name: "Café Soluble Altomayo Gourmet 180g", SKU: "ALT-GOUR-180", Category: "Canasta familiar", TaxCategory: "REDUCIDO", UnitOfMeasure: "UNIDAD"},

	// --- EXENTO (0%) ---
	"PROD-013": {ProductID: "PROD-013", Name: "Agua Mineral San Mateo 600ml", SKU: "AGU-SMT-600", Category: "Agua potable", TaxCategory: "EXENTO", UnitOfMeasure: "BOTELLA"},
	"PROD-014": {ProductID: "PROD-014", Name: "Agua de Mesa Cielo 2.5L", SKU: "AGU-CIE-25", Category: "Agua potable", TaxCategory: "EXENTO", UnitOfMeasure: "BOTELLA"},
	"PROD-015": {ProductID: "PROD-015", Name: "Paracetamol 500mg", SKU: "PARAC-500MG", Category: "Medicamentos", TaxCategory: "EXENTO", UnitOfMeasure: "CAJA"},
	"PROD-016": {ProductID: "PROD-016", Name: "Naproxeno Sódico 550mg", SKU: "NAP-SOD-550MG", Category: "Medicamentos", TaxCategory: "EXENTO", UnitOfMeasure: "CAJA"},
	"PROD-017": {ProductID: "PROD-017", Name: "Limon Acido Bolsa 1kg", SKU: "LIM-AC-1K", Category: "Canasta básica exenta", TaxCategory: "EXENTO", UnitOfMeasure: "BOLSA"},
	"PROD-018": {ProductID: "PROD-018", Name: "Filete de Pechuga de Pollo Bolsa 1kg", SKU: "FIL-POL-1K", Category: "Canasta básica exenta", TaxCategory: "EXENTO", UnitOfMeasure: "BOLSA"},
	"PROD-019": {ProductID: "PROD-019", Name: "Carne Molida Bolsa 1Kg", SKU: "CARN-MOL-1K", Category: "Canasta básica exenta", TaxCategory: "EXENTO", UnitOfMeasure: "BOLSA"},
	"PROD-020": {ProductID: "PROD-020", Name: "Filete de Tilapia Bolsa 1kg", SKU: "FIL-TIL-1K", Category: "Canasta básica exenta", TaxCategory: "EXENTO", UnitOfMeasure: "BOLSA"},
}

func main() {
	port := os.Getenv("PORT")

	if port == "" {
		port = "8081" // Puerto por defecto
	}

	if !strings.HasPrefix(port, ":") {
		port = ":" + port
	}

	http.HandleFunc("/products/", handleGetProduct)

	server := &http.Server{
		Addr:         port,
		WriteTimeout: 5 * time.Second,
		ReadTimeout:  5 * time.Second,
	}

	log.Printf("[PRODUCTS-API] Servidor de Mocks corriendo en el puerto %s", port)

	if err := server.ListenAndServe(); err != nil {
		log.Fatalf("Error al iniciar el servidor de productos: %v", err)
	}
}

func handleGetProduct(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)

		return
	}

	pathParts := strings.Split(r.URL.Path, "/products/")

	if len(pathParts) < 2 || pathParts[1] == "" {
		w.Header().Set("Content-Type", "application/json")

		w.WriteHeader(http.StatusBadRequest)

		json.NewEncoder(w).Encode(map[string]string{"error": "productId es requerido"})

		return
	}

	productId := pathParts[1]

	log.Printf("[PRODUCTS-API] Solicitud recibida para el ID: %s", productId)

	product, exists := productsCatalog[productId]

	w.Header().Set("Content-Type", "application/json")

	if !exists {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(map[string]string{"error": "Producto no encontrado"})
		return
	}

	w.WriteHeader(http.StatusOK)

	json.NewEncoder(w).Encode(product)
}
