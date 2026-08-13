import { compactPageNumbers } from '../compactPagination';

describe('compactPageNumbers', () => {
  it.each([
    [1, 2, [1, 2]],
    [1, 10, [1, 2, 3]],
    [5, 10, [4, 5, 6]],
    [10, 10, [8, 9, 10]],
  ])('keeps page %s of %s within three buttons', (current, total, expected) => {
    expect(compactPageNumbers(current as number, total as number)).toEqual(expected);
  });
});
