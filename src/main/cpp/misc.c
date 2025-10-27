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

void __chkstk_ms(void) {}


typedef unsigned int  size_t;
typedef unsigned char u8;
typedef unsigned int  u32;
typedef long long     s64;
typedef unsigned long long u64;

int strncmp(const char *a,const char *b,size_t n){
  for(;n&&*a&&(*a==*b);++a,++b,--n){} return n?((unsigned char)*a-(unsigned char)*b):0;
}
char *strcpy(char *d,const char *s){char *r=d; while((*d++=*s++)); return r;}
char *strncpy(char *d,const char *s,size_t n){
  char *r=d; while(n&&*s){*d++=*s++;--n;} while(n--){*d++=0;} return r;
}
char *strcat(char *d,const char *s){char *r=d; while(*d)++d; while((*d++=*s++)); return r;}
char *strchr(const char *s,int c){char ch=(char)c; for(;;++s){ if(*s==ch) return (char*)s; if(!*s) return 0; }}
char *strrchr(const char *s,int c){const char*last=0; char ch=(char)c; for(;*s;++s) if(*s==ch) last=s; return (char*)last;}
char *strstr(const char *h,const char *n){
  if(!*n) return (char*)h; size_t nl=strlen(n);
  for(;*h;++h) if(*h==*n){ if(!strncmp(h,n,nl)) return (char*)h; }
  return 0;
}

static int ct_isprint(int c){ return (c>=0x20 && c<=0x7E); }
static int ct_isspace(int c){ return (c==32|| (c>=9&&c<=13)); }
static int ct_isalnum(int c){
  return (c>='0'&&c<='9')||(c>='A'&&c<='Z')||(c>='a'&&c<='z');
}

#if defined(__i386__) || defined(_M_IX86)
int  (__cdecl * _imp__isprint)(int) = ct_isprint;
int  (__cdecl * _imp__isspace)(int) = ct_isspace;
int  (__cdecl * _imp__isalnum)(int) = ct_isalnum;
#endif

static void swap_bytes(u8* a,u8* b,size_t sz){while(sz--){u8 t=*a;*a++=*b;*b++=t;}}
static int cmp_wrap(const void *base,size_t sz,int(*cmp)(const void*,const void*),size_t i,size_t j){
  return cmp((const u8*)base + i*sz, (const u8*)base + j*sz);
}
void qsort(void *base, size_t n, size_t sz, int (*cmp)(const void*, const void*)){
  if(n<2) return;
  size_t l=0, r=n-1;
  while(l<r){
    size_t i=l, j=r, p=l+((r-l)>>1);
    if(cmp_wrap(base,sz,cmp,l,p)>0) swap_bytes((u8*)base+l*sz,(u8*)base+p*sz,sz);
    if(cmp_wrap(base,sz,cmp,p,r)>0) swap_bytes((u8*)base+p*sz,(u8*)base+r*sz,sz);
    if(cmp_wrap(base,sz,cmp,l,p)>0) swap_bytes((u8*)base+l*sz,(u8*)base+p*sz,sz);
    const u8* pv = (const u8*)base + p*sz;
    do{
      while(cmp((const u8*)base+i*sz, pv)<0) ++i;
      while(cmp((const u8*)base+j*sz, pv)>0) --j;
      if(i<=j){ swap_bytes((u8*)base+i*sz,(u8*)base+j*sz,sz); ++i; if(j) --j; }
    }while(i<=j);
    if(j-l < r-i){ if(l<j) { r=j; continue; } l=i; }
    else { if(i<r){ l=i; continue; } r=j; }
  }
}

typedef void (*sighandler_t)(int);
#define SIG_DFL ((sighandler_t)0)
#define SIG_IGN ((sighandler_t)1)
sighandler_t signal(int sig, sighandler_t h){ (void)sig; (void)h; return SIG_DFL; }

static int itoa_u(char* buf, u64 v, int base){
  static const char* D="0123456789abcdef";
  char tmp[32]; int n=0; if(v==0){ buf[0]='0'; return 1; }
  while(v){ tmp[n++]=D[v%base]; v/=base; }
  for(int i=0;i<n;++i) buf[i]=tmp[n-1-i];
  return n;
}
static int itoa_s(char* buf, s64 v){
  if(v<0){ *buf='-'; return 1+itoa_u(buf+1,(u64)(-v),10); }
  return itoa_u(buf,(u64)v,10);
}
int vsprintf(char* out, const char* fmt, __builtin_va_list ap){
  return vsnprintf(out, (size_t)-1, fmt, ap);
}
int __mingw_vsprintf(char* out, const char* fmt, __builtin_va_list ap){
  return vsprintf(out, fmt, ap);
}
int printf(const char* fmt, ...){
  char buf[1024];
  __builtin_va_list ap; __builtin_va_start(ap, fmt);
  int n = vsnprintf(buf, sizeof(buf)-1, fmt, ap);
  __builtin_va_end(ap);
  (void)n;
  return n;
}
int __mingw_printf(const char* fmt, ...){
  __builtin_va_list ap; __builtin_va_start(ap, fmt);
  char buf[1024];
  int n = vsnprintf(buf, sizeof(buf)-1, fmt, ap);
  __builtin_va_end(ap);
  return n;
}

s64 __divmoddi4(s64 a, s64 b, s64* rem){
  s64 q = a / b;
  if(rem) *rem = a % b;
  return q;
}
