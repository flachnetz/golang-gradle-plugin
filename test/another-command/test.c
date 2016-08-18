#include "test.h"

/**
 * super-duper fast summing of values
 */
int64_t fast_sum(int64_t *values, int64_t n) {
  int64_t sum = 0;
  while(--n >= 0) {
    sum += *(values++);
  }

  return sum;
}
