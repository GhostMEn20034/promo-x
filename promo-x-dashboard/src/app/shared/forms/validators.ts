import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Like promo-x-core's `@NotBlank` and `@Size`, but on the trimmed value, which is what the forms send.
 * Fails with `required` when blank, or `length: { min, max }` when out of range.
 */
export function trimmedLength(min: number, max: number): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const length = control.value.trim().length;
    if (length === 0) {
      return { required: true };
    }
    return length < min || length > max ? { length: { min, max } } : null;
  };
}
