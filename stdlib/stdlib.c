#include <stdio.h>
#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

// input / output

void print_int64(int64_t value) {
    printf("%" PRId64 "\n", value);
}

int64_t read_int64() {
    int64_t value;
    scanf("%" SCNd64, &value);
    return value;
}

// internal procedures
// these functions have names starting with "_$" which makes them not accessible from our language
void* _$checked_malloc(size_t size) {
    void* address = malloc(size);
    if (size > 0 && address == NULL) {
        fprintf(stderr, "%s\n", strerror(ENOMEM));
        exit(1);
    }
    return address;
}

// generators

typedef int64_t generator_id_t;
typedef int64_t generator_state_t;

typedef struct {
    int64_t value;
    generator_state_t state;
} resume_result_t;

generator_id_t int64_range_init(int64_t max) {
    return max;
}

resume_result_t int64_range_resume(generator_id_t max, generator_state_t value) {
    resume_result_t result;

    if (value < max) {
        result.value = value;
        result.state = value + 1;
    } else
        result.state = 0;

    return result;
}

void int64_range_finalize(generator_id_t id) { }

generator_id_t int64_input_init() {
    return 0;
}

resume_result_t int64_input_resume(generator_id_t id, generator_state_t state) {
    resume_result_t result;
    result.state = scanf("%" SCNd64, &result.value) != EOF ? 1 : 0;
    return result;
}

void int64_input_finalize(generator_id_t id) { }
