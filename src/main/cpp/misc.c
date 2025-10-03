/* minimal_sscanf.c — sscanf with %d and %[chars] (width supported), no headers */

#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

typedef __builtin_va_list va_list;
#define va_start __builtin_va_start
#define va_arg   __builtin_va_arg
#define va_end   __builtin_va_end

/* tiny helpers (no ctype.h) */
static int my_isspace(int c){ return c==' '||c=='\t'||c=='\n'||c=='\r'||c=='\f'||c=='\v'; }
static int my_isdigit(int c){ return c>='0' && c<='9'; }

/* parse (optional) decimal width like %12d / %8[ab] */
static const char* parse_width(const char* fmt, int* outw){
    int w = 0; int any = 0;
    while (my_isdigit(*fmt)) { any = 1; w = w*10 + (*fmt - '0'); fmt++; }
    *outw = any ? w : -1; /* -1 means “no width specified” */
    return fmt;
}

int __attribute__((cdecl)) __mingw_sscanf(const char* src, const char* fmt, ...) {
    va_list ap; va_start(ap, fmt);
    const char* s = src;
    int assigned = 0;

    while (*fmt) {
        if (*fmt != '%') {
            /* whitespace in format skips any whitespace in input */
            if (my_isspace((unsigned char)*fmt)) {
                while (my_isspace((unsigned char)*s)) s++;
                fmt++;
                continue;
            }
            /* literal match */
            if (*fmt != *s) { break; } /* mismatch terminates */
            fmt++; s++;
            continue;
        }

        /* conversion starts */
        fmt++;  /* skip '%' */

        /* optional width */
        int width = -1;
        fmt = parse_width(fmt, &width);

        if (*fmt == 'd') {
            /* %d : signed decimal integer */
            int neg = 0, any = 0, val = 0;
            /* skip leading whitespace in input (scanf behavior) */
            while (my_isspace((unsigned char)*s)) s++;

            /* sign */
            if (*s == '+' || *s == '-') { neg = (*s == '-'); s++; if (width > 0) width--; }

            /* digits */
            while (*s && my_isdigit((unsigned char)*s) && (width != 0)) {
                any = 1;
                val = val*10 + (*s - '0');
                s++;
                if (width > 0) width--;
            }
            if (!any) { /* no digits -> fail this conversion */
                break;
            }
            if (neg) val = -val;

            int* out = (int*)va_arg(ap, int*);
            *out = val;
            assigned++;
            fmt++; /* past 'd' */
            continue;
        }

        if (*fmt == '[') {
            /* scanset: %[chars] (no ranges, no ^-negation in this minimal version) */
            fmt++; /* after '[' */
            const char* setbeg = fmt;
            /* find closing ']' */
            while (*fmt && *fmt != ']') fmt++;
            if (*fmt != ']') {
                /* malformed format: no closing ] — stop */
                break;
            }
            const char* setend = fmt; /* points to ']' */
            fmt++; /* past ']' */

            /* destination buffer */
            char* out = (char*)va_arg(ap, char*);

            /* build a simple membership test: linear scan over set each time */
            int count = 0;
            /* scanf behavior: skip nothing here (no implicit whitespace skip for %[) */
            while (*s) {
                char c = *s;
                /* stop on width exhaustion (if width specified) */
                if (width == 0) break;

                /* check membership: c must be one of chars in [setbeg, setend) */
                int ok = 0;
                for (const char* p = setbeg; p < setend; ++p) {
                    if (c == *p) { ok = 1; break; }
                }
                if (!ok) break;

                out[count++] = c;
                s++;
                if (width > 0) width--;
            }

            if (count == 0) {
                /* no assignment occurred for this conversion -> fail */
                break;
            }

            out[count] = '\0';
            assigned++;
            continue;
        }

        /* unsupported specifier -> treat as literal match with the char (common permissive fallback) */
        if (*fmt) {
            if (*fmt != *s) { break; }
            fmt++; s++;
            continue;
        }
    }

    va_end(ap);
    return assigned;
}

#pragma pack(push, 4)
typedef struct _iobuf {
    char* _ptr;
    int   _cnt;
    char* _base;
    int   _flag;
    int   _file;
    int   _charbuf;
    int   _bufsiz;
    char* _tmpfname;
} FILE;
#pragma pack(pop)

inline int vsnprintf(char *s, size_t n, const char *fmt, va_list ap)
{ 
	int r;
	char b;
	FILE f;
    
	if (n-1 > INT_MAX-1) {
		if (n) {
			//todo errno = EOVERFLOW;
			return -1;
		}
		s = &b;
		n = 1;
	}

    f._base = s;
    f._ptr = s;
    f._flag = 66;
    f._cnt = n;

    int (__attribute__((cdecl)) *vfprintf)(void*, const char*, va_list);
    vfprintf = (int (__attribute__((cdecl)) *)(void*, const char*, va_list))0x61DB99;
    
	r = vfprintf(&f, fmt, ap);
    if (f._ptr - s >= n) s[n - 1] = 0;
    else *f._ptr = 0;

	return r;
}

int __attribute__((cdecl)) __mingw_sprintf(char* dst, const char* fmt, ...) {
    va_list ap; va_start(ap, fmt);
    int r = vsnprintf(dst, 0x7fffffff, fmt, ap);
    va_end(ap);
    return r;
}


int __attribute__((cdecl)) __mingw_vsnprintf(char *s, size_t n, const char *fmt, va_list ap) {
    return vsnprintf(s, n, fmt, ap);
}
