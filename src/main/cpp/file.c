/* memfs_all.c — in-memory FILE API with read-only predefined files
   - Implements: fopen, fclose, fread, fwrite, fseek, ftell, fflush, feof, ferror
                 remove, rename, _stat32 (+ __imp__stat32 / __imp___stat32 aliases)
   - Adds: memfs_add_ro(name, data, size) to register RO files backed by external memory.
   - Uses malloc/realloc/free; swap them with your own if needed.
   - No stdio.h includes to avoid clashes with host CRT.
*/
#include "jvmspi.h"
#include "file.h"

/* ---------------- Configuration ---------------- */
#ifndef MEMFS_MAX_FILES
#define MEMFS_MAX_FILES 256
#endif

#ifndef MEMFS_NAME_MAX
#define MEMFS_NAME_MAX 260
#endif

/* ---------------- Types ---------------- */
typedef struct MEMFS_FILE {
    char   name[MEMFS_NAME_MAX];
    unsigned char *data;
    size_t size;      /* current file size */
    size_t cap;       /* allocated capacity (==size for RO) */
    size_t pos;       /* current file position */
    int    readable;
    int    writable;
    int    append;
    int    eof;
    int    err;
    int    open;
} MEMFS_FILE;

typedef struct {
    char   name[MEMFS_NAME_MAX];
    unsigned char *data;
    size_t size;
    size_t cap;
    int    refcnt;    /* open handle count */
    unsigned char read_only; /* 1 = backed by external RO buffer */
    unsigned char owned;     /* 1 = data owned by memfs (malloc/realloc/free) */
} MEMFS_ENTRY;

/* Global in-memory “filesystem” */
static MEMFS_ENTRY g_fs[MEMFS_MAX_FILES];
static MEMFS_FILE* g_open[MEMFS_MAX_FILES];

/* To match signatures without pulling stdio.h */
typedef struct FILE FILE;

/* ---------------- Small string/utility helpers (no CRT deps) ---------------- */
static size_t strnlen_s(const char* s, size_t m){
    size_t i=0; if(!s) return 0; for(; i<m && s[i]; ++i); return i;
}
static int streq(const char* a, const char* b){
    if(!a||!b) return 0; for(; *a || *b; ++a,++b){ if(*a!=*b) return 0; } return 1;
}
static void* xrealloc(void* p, size_t n){
    if(n == 0) n = 1;
    void* q = realloc(p, n);
    return q;
}

/* Grow backing buffer to at least want_end bytes; fails for RO or !owned entries */
static int grow(MEMFS_ENTRY* e, size_t want_end){
    if (want_end <= e->cap) return 1;
    if (!e->owned || e->read_only) return 0; /* cannot grow read-only or foreign-backed entries */
    size_t newcap = e->cap ? e->cap : 256;
    while (newcap < want_end) {
        newcap = newcap < (1u<<20) ? (newcap << 1) : (newcap + (1u<<20)); /* double, then +1MB */
    }
    void* nd = xrealloc(e->data, newcap);
    if(!nd) return 0;
    if (newcap > e->cap) { /* zero-fill new tail (optional) */
        unsigned char* p = (unsigned char*)nd;
        for (size_t i=e->cap; i<newcap; ++i) p[i]=0;
    }
    e->data = (unsigned char*)nd;
    e->cap  = newcap;
    return 1;
}

static int parse_mode(const char* mode, int* r, int* w, int* a){
    int plus=0; *r=*w=*a=0;
    if(!mode || !mode[0]) return 0;
    char m0 = mode[0];
    for (const char* p=mode; *p; ++p) if(*p=='+') plus=1;
    if (m0=='r') { *r=1; if(plus)*w=1; }
    else if (m0=='w'){ *w=1; if(plus)*r=1; }
    else if (m0=='a'){ *w=1; *a=1; if(plus)*r=1; }
    else return 0;
    return 1;
}

static int fs_index_of(const char* name){
    if (!name) return -1;
    for (int i=0;i<MEMFS_MAX_FILES;++i)
        if (g_fs[i].name[0] && streq(g_fs[i].name, name)) return i;
    return -1;
}

static MEMFS_ENTRY* find_or_create(const char* name, int create, int trunc, int *is_new){
    if(is_new) *is_new=0;
    int idx = fs_index_of(name);
    if (idx >= 0) {
        if (trunc) {
            if (g_fs[idx].read_only) return NULL; /* cannot truncate RO */
            g_fs[idx].size = 0;
            if (!grow(&g_fs[idx], 0)) return NULL;
        }
        return &g_fs[idx];
    }
    if (!create) return NULL;
    for (int i=0;i<MEMFS_MAX_FILES;++i){
        if(!g_fs[i].name[0]){
            size_t n = strnlen_s(name, MEMFS_NAME_MAX-1);
            for (size_t k=0;k<n;++k) g_fs[i].name[k]=name[k];
            g_fs[i].name[n]=0;
            g_fs[i].data=NULL; g_fs[i].size=0; g_fs[i].cap=0; g_fs[i].refcnt=0;
            g_fs[i].read_only = 0;
            g_fs[i].owned = 1;
            if(is_new) *is_new=1;
            return &g_fs[i];
        }
    }
    return NULL;
}

static MEMFS_FILE* alloc_stream(void){
    for (int i=0;i<MEMFS_MAX_FILES;++i){
        if(!g_open[i]) {
            g_open[i] = (MEMFS_FILE*)malloc(sizeof(MEMFS_FILE));
            return g_open[i];
        }
    }
    return NULL;
}
static void free_stream(MEMFS_FILE* f){
    if(!f) return;
    for (int i=0;i<MEMFS_MAX_FILES;++i) if (g_open[i]==f){ g_open[i]=NULL; break; }
    free(f);
}

/* ---------------- Public: register read-only pre-defined file ---------------- */
int memfs_add_ro(const char* name, const void* data, size_t size) {
    if (!name || !data) return -1;
    if (fs_index_of(name) >= 0) return -1; /* already exists */
    for (int i=0;i<MEMFS_MAX_FILES;++i){
        if (!g_fs[i].name[0]){
            size_t n = strnlen_s(name, MEMFS_NAME_MAX-1);
            for (size_t k=0;k<n;++k) g_fs[i].name[k]=name[k];
            g_fs[i].name[n]=0;
            g_fs[i].data  = (unsigned char*)(uintptr_t)(const void*)data; /* cast away const for internal API */
            g_fs[i].size  = size;
            g_fs[i].cap   = size;
            g_fs[i].refcnt= 0;
            g_fs[i].read_only = 1;
            g_fs[i].owned = 0; /* do not free */
            return 0;
        }
    }
    return -1; /* no slots */
}

/* ---------------- FILE-like API ---------------- */

FILE* fopen(const char* filename, const char* mode){
    int r=0,w=0,a=0;
    if(!parse_mode(mode, &r,&w,&a)) return NULL;

    int existing = fs_index_of(filename);
    if (existing >= 0 && g_fs[existing].read_only) {
        /* RO file: allow only pure-read (no '+', no writing/appending) */
        if (!r || w || a) return NULL;
    }

    int trunc = (mode && mode[0]=='w');
    MEMFS_ENTRY* e = find_or_create(filename, (existing<0) ? (w||a) : 0, trunc, NULL);
    if(!e) {
        /* if exists and is RO, still allow reading */
        if (existing >= 0 && g_fs[existing].read_only && r) e = &g_fs[existing];
        else return NULL;
    }

    MEMFS_FILE* f = alloc_stream();
    if(!f) return NULL;

    size_t n = strnlen_s(filename, MEMFS_NAME_MAX-1);
    for (size_t k=0;k<n;++k) f->name[k]=filename[k];
    f->name[n]=0;

    f->data  = e->data;
    f->size  = e->size;
    f->cap   = e->cap;
    f->pos   = a ? f->size : 0;
    f->readable = r;
    f->writable = w && !e->read_only;
    f->append   = a && !e->read_only;
    f->eof= (f->pos >= f->size);
    f->err=0; f->open=1;

    e->refcnt++;
    return (FILE*)f;
}

int fclose(FILE* fp){
    MEMFS_FILE* f = (MEMFS_FILE*)fp;
    if(!f || !f->open) return -1;

    int idx = fs_index_of(f->name);
    if (idx >= 0) {
        /* sync back to directory (size/cap/data may have changed for RW files) */
        if (!g_fs[idx].read_only) {
            g_fs[idx].data = f->data;
            g_fs[idx].size = f->size;
            g_fs[idx].cap  = f->cap;
        }
        if (g_fs[idx].refcnt>0) g_fs[idx].refcnt--;
    }

    f->open=0;
    free_stream(f);
    return 0;
}

size_t fread(void* buf, size_t sz, size_t cnt, FILE* fp){
    MEMFS_FILE* f = (MEMFS_FILE*)fp;
    if(!f || !f->open || !f->readable) return 0;
    size_t want = sz*cnt;
    if (want==0) return 0;
    if (f->pos >= f->size){ f->eof=1; return 0; }

    size_t rem = f->size - f->pos;
    size_t take = (want < rem) ? want : rem;
    unsigned char* dst = (unsigned char*)buf;
    for(size_t i=0;i<take;++i) dst[i] = f->data[f->pos + i];
    f->pos += take;
    f->eof = (f->pos >= f->size);
    return sz ? (take / sz) : 0;
}

size_t fwrite(const void* buf, size_t sz, size_t cnt, FILE* fp){
    MEMFS_FILE* f = (MEMFS_FILE*)fp;
    if(!f || !f->open || !f->writable) return 0;
    size_t want = sz*cnt;
    if (want==0) return 0;

    int idx = fs_index_of(f->name);
    if (idx < 0){ f->err=1; return 0; }
    MEMFS_ENTRY* e = &g_fs[idx];

    if (e->read_only) { f->err=1; return 0; } /* guard */

    if (f->append) f->pos = f->size;

    size_t endpos = f->pos + want;
    if (endpos > e->cap) {
        if (!grow(e, endpos)) { f->err=1; return 0; }
        f->data = e->data; f->cap = e->cap;
    }
    /* zero-fill hole if seeking past end before write */
    if (f->pos > f->size) {
        for (size_t k=f->size; k<f->pos; ++k) f->data[k]=0;
    }

    const unsigned char* src = (const unsigned char*)buf;
    for (size_t i=0;i<want;++i) f->data[f->pos + i] = src[i];
    f->pos += want;
    if (f->pos > f->size) { f->size = f->pos; e->size = f->size; }
    return sz ? (want / sz) : 0;
}

int fseek(FILE* fp, long off, int origin){
    MEMFS_FILE* f = (MEMFS_FILE*)fp;
    if(!f || !f->open) return -1;
    size_t base=0;
    if (origin==0) base=0;
    else if (origin==1) base=f->pos;
    else if (origin==2) base=f->size;
    else return -1;

    long long np = (long long)base + (long long)off;
    if (np < 0) { f->err=1; return -1; }
    f->pos = (size_t)np;
    f->eof = (f->pos >= f->size);
    return 0;
}

long ftell(FILE* fp){
    MEMFS_FILE* f = (MEMFS_FILE*)fp;
    if(!f || !f->open) return -1L;
    return (long)f->pos;
}

int fflush(FILE* fp){
    (void)fp; /* memory-backed; nothing to flush */
    return 0;
}

int feof(FILE* fp){
    MEMFS_FILE* f = (MEMFS_FILE*)fp;
    if(!f || !f->open) return 1;
    return f->eof ? 1 : 0;
}

int ferror(FILE* fp){
    MEMFS_FILE* f = (MEMFS_FILE*)fp;
    if(!f || !f->open) return 1;
    return f->err ? 1 : 0;
}

/* ---------------- remove / rename ---------------- */

int remove(const char* path) {
    int idx = fs_index_of(path);
    if (idx < 0) return -1;              /* not found */
    MEMFS_ENTRY* e = &g_fs[idx];
    if (e->read_only) return -1;         /* refuse to remove RO files */
    if (e->refcnt > 0) return -1;        /* opened */

    if (e->owned && e->data) { free(e->data); }
    e->data = NULL; e->size = e->cap = 0;
    e->name[0] = 0;
    e->refcnt = 0;
    e->owned = 1; e->read_only = 0;
    return 0;
}

int rename(const char* oldp, const char* newp) {
    if (!oldp || !newp) return -1;
    if (streq(oldp, newp)) return 0;

    int oldi = fs_index_of(oldp);
    if (oldi < 0) return -1;
    MEMFS_ENTRY* src = &g_fs[oldi];

    if (src->read_only) return -1; /* disallow renaming RO files */

    int newi = fs_index_of(newp);
    if (newi >= 0) {
        MEMFS_ENTRY* dst = &g_fs[newi];
        if (dst->refcnt > 0) return -1;  /* target open -> fail */
        if (dst->read_only) return -1;   /* cannot overwrite RO target */
        if (dst->owned && dst->data) { free(dst->data); }
        dst->data = NULL; dst->size = dst->cap = 0;
        dst->name[0] = 0;
        dst->refcnt = 0;
        dst->owned = 1; dst->read_only = 0;
    }

    size_t n = strnlen_s(newp, MEMFS_NAME_MAX-1);
    for (size_t k=0;k<n;++k) src->name[k] = newp[k];
    src->name[n] = 0;

    for (int i=0;i<MEMFS_MAX_FILES;++i) {
        MEMFS_FILE* f = g_open[i];
        if (f && f->open && streq(f->name, oldp)) {
            size_t m = strnlen_s(newp, MEMFS_NAME_MAX-1);
            for (size_t k=0;k<m;++k) f->name[k] = newp[k];
            f->name[m] = 0;
        }
    }
    return 0;
}

/* ---------------- _stat32 and import aliases ---------------- */

typedef long  time32_t;

#ifndef _S_IFREG
#define _S_IFREG  0x8000
#endif
#ifndef _S_IREAD
#define _S_IREAD  0x0100
#endif
#ifndef _S_IWRITE
#define _S_IWRITE 0x0080
#endif

struct _stat32 {
    unsigned int st_dev;
    unsigned short st_ino;
    unsigned short st_mode;
    short  st_nlink;
    short  st_uid;
    short  st_gid;
    unsigned int st_rdev;
    long   st_size;
    time32_t st_atime;
    time32_t st_mtime;
    time32_t st_ctime;
};

int _stat32(const char* path, struct _stat32* st){
    if (!path || !st) return -1;
    int idx = fs_index_of(path);
    if (idx < 0) return -1;
    MEMFS_ENTRY* e = &g_fs[idx];

    st->st_dev = 0; st->st_ino = 0;
    st->st_mode = (unsigned short)(_S_IFREG | _S_IREAD | (e->read_only ? 0 : _S_IWRITE));
    st->st_nlink = 1; st->st_uid = 0; st->st_gid = 0; st->st_rdev = 0;
    st->st_size = (long)e->size;
    st->st_atime = st->st_mtime = st->st_ctime = 0;
    return 0;
}

/* Provide import-style aliases some object files reference when expecting MSVCRT thunks */
void* __imp__stat32  = (void*)&_stat32;
void* __imp___stat32 = (void*)&_stat32;
void* _imp___stat32 = (void*)&_stat32;

/* ---------------- End of memfs_all.c ---------------- */


