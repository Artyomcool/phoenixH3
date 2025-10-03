
#include <stddef.h>
#include <stdint.h>

int memcmp(const void *a, const void *b, size_t n) {
    const unsigned char *x = (const unsigned char *)a;
    const unsigned char *y = (const unsigned char *)b;
    for (; n; --n, ++x, ++y) {
        if (*x != *y) {
            return (int)*x - (int)*y;  // <0, 0, >0
        }
    }
    return 0;
}

void *memcpy(void *dst, const void *src, size_t n) {
    unsigned char       *d = (unsigned char *)dst;
    const unsigned char *s = (const unsigned char *)src;
    while (n--) {
        *d++ = *s++;
    }
    return dst;
}

void *memmove(void *dst, const void *src, size_t n) {
    unsigned char       *d = (unsigned char *)dst;
    const unsigned char *s = (const unsigned char *)src;

    if (d == s || n == 0) return dst;

    if (d < s) {
        while (n--) {
            *d++ = *s++;
        }
    } else {
        d += n;
        s += n;
        while (n--) {
            *--d = *--s;
        }
    }
    return dst;
}

void *memset(void *dst, int c, size_t n) {
    unsigned char *d = (unsigned char *)dst;
    unsigned char  v = (unsigned char)c;
    while (n--) {
        *d++ = v;
    }
    return dst;
}

/* -------- strings (ASCII, NUL-terminated) -------- */

int strcmp(const char *a, const char *b) {
    const unsigned char *x = (const unsigned char *)a;
    const unsigned char *y = (const unsigned char *)b;
    while (*x && (*x == *y)) {
        ++x; ++y;
    }
    return (int)*x - (int)*y;  // <0, 0, >0
}

size_t strlen(const char *s) {
    const char *p = s;
    while (*p) ++p;
    return (size_t)(p - s);
}

static inline uint64_t udivmod64(uint64_t n, uint64_t d, uint64_t* rem_out) {
    uint64_t q = 0, r = 0;
    for (int i = 63; i >= 0; --i) {
        r = (r << 1) | ((n >> i) & 1u);
        if (r >= d) { r -= d; q |= (1ULL << i); }
    }
    if (rem_out) *rem_out = r;
    return q;
}

/* ---------------- unsigned helpers ---------------- */

uint64_t __udivdi3(uint64_t a, uint64_t b) {
    if (!b) { /* UB: деление на ноль */ return 0; }
    return udivmod64(a, b, 0);
}

uint64_t __umoddi3(uint64_t a, uint64_t b) {
    if (!b) { /* UB */ return 0; }
    uint64_t r;
    (void)udivmod64(a, b, &r);
    return r;
}

/* ---------------- signed helpers ---------------- */

int64_t __divdi3(int64_t a, int64_t b) {
    if (!b) { /* UB */ return 0; }
    uint64_t ua = (a < 0) ? (uint64_t)(-a) : (uint64_t)a;
    uint64_t ub = (b < 0) ? (uint64_t)(-b) : (uint64_t)b;

    uint64_t q = udivmod64(ua, ub, 0);

    if ((a < 0) ^ (b < 0)) {
        return -(int64_t)q;
    }
    return (int64_t)q;
}

int64_t __moddi3(int64_t a, int64_t b) {
    if (!b) { /* UB */ return 0; }
    uint64_t ua = (a < 0) ? (uint64_t)(-a) : (uint64_t)a;
    uint64_t ub = (b < 0) ? (uint64_t)(-b) : (uint64_t)b;

    uint64_t r;
    (void)udivmod64(ua, ub, &r);

    return (a < 0) ? -(int64_t)r : (int64_t)r;
}


