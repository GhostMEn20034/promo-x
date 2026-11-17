// Public API of the category module. Other modules import from here only, never from `internal/`.
export { categoryRoutes } from './category.routes';
export { type Category, type CategoryRef } from './internal/category.model';
export { CategoryPickerDialog } from './internal/picker/category-picker-dialog';
