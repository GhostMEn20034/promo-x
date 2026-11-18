// Public API of the product module. Other modules import from here only, never from `internal/`.
export { productRoutes } from './product.routes';
export { type Product, type ProductRef } from './internal/product.model';
export { ProductPickerDialog } from './internal/picker/product-picker-dialog';
