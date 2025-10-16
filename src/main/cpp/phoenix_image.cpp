/*
 * section:// reader — jchar* paths, no conversion for non-section paths
 *
 * - __wrap_OsFile_open(const jchar* filename, const char* mode)
 * - __wrap_OsFile_exists(const jchar* filename)
 * - __wrap_OsFile_remove(const jchar* filename)
 * - __wrap_OsFile_rename(const jchar* oldname, const jchar* newname)
 *
 * If path starts with section:// (checked in UTF-16), extract ASCII section name
 * (up to 8 chars) from the jchar* and map to PE section in current module.
 *
 * Uses low-bit tagged pointer for memfile (pointer | 1).
 * Minimal logs: success/failure for opening section, and read counts/EOF.
 */

#define _CRT_SECURE_NO_WARNINGS
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>
#include <stdarg.h>
#include "jvm.h"
#include "jvmspi.h"

typedef uint16_t jchar; /* JNI jchar */
typedef FILE *OsFile_Handle;

extern "C" IMAGE_DOS_HEADER __ImageBase;

static void log_pe(const char* fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    int n = _vsnprintf(buf, sizeof(buf)-1, fmt, ap);
    va_end(ap);
    if (n < 0) n = (int)sizeof(buf) - 1;
    buf[n] = '\0';
    fputs(buf, stderr);
    fputc('\n', stderr);
}

static const char* name8_to_cstr(const char name8[8], char out[9]) {
    memcpy(out, name8, 8);
    out[8] = '\0';
    for (int i = 0; i < 8; ++i) { if (out[i] == '\0') break; }
    return out;
}

static bool find_pe_section(const char name8[8], const uint8_t** data, size_t* size) {
    if (!name8 || !data || !size) {
        log_pe("find_pe_section: invalid args name8=%p data=%p size=%p", (void*)name8, (void*)data, (void*)size);
        return false;
    }

    char wanted[9];
    log_pe("find_pe_section: looking for section '%s'", name8_to_cstr(name8, wanted));

    const uint8_t* base = reinterpret_cast<const uint8_t*>(&__ImageBase);
    log_pe("  module base: %p", (const void*)base);

    const IMAGE_DOS_HEADER* dos = reinterpret_cast<const IMAGE_DOS_HEADER*>(base);
    if (!dos) { log_pe("  DOS header ptr is null"); return false; }

    if (dos->e_magic != IMAGE_DOS_SIGNATURE) {
        log_pe("  bad DOS signature: 0x%04X (expected 0x%04X)", dos->e_magic, IMAGE_DOS_SIGNATURE);
        return false;
    }
    log_pe("  DOS OK, e_lfanew = 0x%X", (unsigned)dos->e_lfanew);

    const uint8_t* ntBase = base + dos->e_lfanew;
    const DWORD signature = *reinterpret_cast<const DWORD*>(ntBase);
    if (signature != IMAGE_NT_SIGNATURE) {
        log_pe("  bad NT signature: 0x%08lX (expected 0x%08X)", (unsigned long)signature, IMAGE_NT_SIGNATURE);
        return false;
    }
    log_pe("  NT signature OK at %p", (const void*)ntBase);

    const IMAGE_FILE_HEADER* fileHeader = reinterpret_cast<const IMAGE_FILE_HEADER*>(ntBase + sizeof(DWORD));
    if (!fileHeader) { log_pe("  fileHeader is null"); return false; }

    log_pe("  Machine=0x%04X Sections=%u SizeOfOptionalHeader=%u",
           fileHeader->Machine, fileHeader->NumberOfSections, fileHeader->SizeOfOptionalHeader);

    const uint8_t* optHdrBase = ntBase + sizeof(DWORD) + sizeof(IMAGE_FILE_HEADER);
    if (optHdrBase == nullptr) { log_pe("  optHdrBase is null"); return false; }

    const WORD magic = *reinterpret_cast<const WORD*>(optHdrBase);
    log_pe("  OptionalHeader.Magic=0x%04X", magic);

    const IMAGE_SECTION_HEADER* firstSection = nullptr;
    if (magic == IMAGE_NT_OPTIONAL_HDR32_MAGIC) {
        const IMAGE_OPTIONAL_HEADER32* opt32 = reinterpret_cast<const IMAGE_OPTIONAL_HEADER32*>(optHdrBase);
        firstSection = reinterpret_cast<const IMAGE_SECTION_HEADER*>((const uint8_t*)opt32 + fileHeader->SizeOfOptionalHeader);
        log_pe("  PE32: SectionAlignment=0x%X FileAlignment=0x%X",
               opt32->SectionAlignment, opt32->FileAlignment);
    } else if (magic == IMAGE_NT_OPTIONAL_HDR64_MAGIC) {
        const IMAGE_OPTIONAL_HEADER64* opt64 = reinterpret_cast<const IMAGE_OPTIONAL_HEADER64*>(optHdrBase);
        firstSection = reinterpret_cast<const IMAGE_SECTION_HEADER*>((const uint8_t*)opt64 + fileHeader->SizeOfOptionalHeader);
        log_pe("  PE32+: SectionAlignment=0x%X FileAlignment=0x%X",
               opt64->SectionAlignment, opt64->FileAlignment);
    } else {
        log_pe("  unknown OptionalHeader.Magic");
        return false;
    }

    if (!firstSection) { log_pe("  firstSection is null"); return false; }

    const IMAGE_SECTION_HEADER* sec = firstSection;
    for (unsigned i = 0; i < fileHeader->NumberOfSections; ++i, ++sec) {
        char secName[9];
        name8_to_cstr((const char*)sec->Name, secName);

        log_pe("  [%u] Name='%.8s' VA=0x%08X VSz=0x%08X RawPtr=0x%08X RawSz=0x%08X Char=0x%08X",
               i, secName,
               sec->VirtualAddress,
               sec->Misc.VirtualSize,
               sec->PointerToRawData,
               sec->SizeOfRawData,
               sec->Characteristics);

        if (memcmp(sec->Name, name8, 8) == 0) {
            uint32_t rva   = sec->VirtualAddress;
            uint32_t vsize = sec->Misc.VirtualSize;
            const uint8_t* ptrInMemory = base + rva;

            *data = ptrInMemory;
            *size = vsize;

            log_pe("  --> FOUND: '%s' at %p, size 0x%X (%u)", secName, (const void*)ptrInMemory, vsize, vsize);
            return true;
        }
    }

    log_pe("  section '%s' not found", wanted);
    return false;
}

static bool resolve_section_by_marker(const char* name8, const uint8_t** data, size_t* size) {
    size_t secSize = 0;
    if (!find_pe_section(name8, data, &secSize)) {
        return false;
    }
    *size = secSize;

    return true;
}

static inline int is_section_uri_jchar(const jchar* s) {
    if (!s) return 0;
    const jchar pref[10] = {
        (jchar)'s',(jchar)'e',(jchar)'c',(jchar)'t',(jchar)'i',
        (jchar)'o',(jchar)'n',(jchar)':',(jchar)'/',(jchar)'/'
    };
    for (int i = 0; i < 10; ++i) {
        if (s[i] != pref[i]) return 0;
    }
    return 1;
}

static bool resolve_section_uri_jchar(const jchar* uri, const uint8_t** data, size_t* size) {
    if (!uri) return false;
    if (!is_section_uri_jchar(uri)) return false;
    const jchar* p = uri + 10;

    char name8[8] = {0};
    size_t i = 0;
    while (p[i] && i < 8) {
        jchar ch = p[i];
        if (ch > 0x7F) {
            return false;
        }
        name8[i] = (char)ch;
        ++i;
    }
    if (i == 0) {
        return false;
    }

    return resolve_section_by_marker(name8, data, size);
}

struct MemFile {
    const uint8_t* data;
    size_t size;
    size_t pos;
    int error;
    int eof;
};

static inline int is_memfile_handle(OsFile_Handle h) {
    return ((uintptr_t)h & 1u) != 0;
}
static inline MemFile* to_memfile(OsFile_Handle h) {
    return (MemFile*)((uintptr_t)h & ~(uintptr_t)1u);
}
static inline OsFile_Handle from_memfile(MemFile* m) {
    return (OsFile_Handle)((uintptr_t)m | 1u);
}

extern "C" OsFile_Handle __real_OsFile_open  (const jchar *filename, const char *mode);
extern "C" int            __real_OsFile_close (OsFile_Handle);
extern "C" int            __real_OsFile_flush (OsFile_Handle);
extern "C" int            __real_OsFile_read  (OsFile_Handle, void* buf, size_t size, size_t count);
extern "C" int            __real_OsFile_write (OsFile_Handle, const void* buf, size_t size, size_t count);
extern "C" long           __real_OsFile_length(OsFile_Handle);
extern "C" int            __real_OsFile_exists(const jchar* filename);
extern "C" int            __real_OsFile_seek  (OsFile_Handle, long offset, int whence);
extern "C" int            __real_OsFile_error (OsFile_Handle);
extern "C" int            __real_OsFile_eof   (OsFile_Handle);
extern "C" int            __real_OsFile_remove(const jchar* filename);
extern "C" int            __real_OsFile_rename(const jchar* oldname, const jchar* _new);

extern "C" OsFile_Handle __wrap_OsFile_open(const jchar *filename, const char *mode) {
    if (!filename) return NULL;

    if (is_section_uri_jchar(filename)) {
        if (mode && (strchr(mode, 'w') || strchr(mode, 'a') || strchr(mode, '+'))) {
            errno = EPERM;
            return NULL;
        }
        const uint8_t* data = NULL; size_t size = 0;
        if (!resolve_section_uri_jchar(filename, &data, &size)) {
            errno = ENOENT;
            return NULL;
        }
        MemFile* m = (MemFile*)malloc(sizeof(MemFile));
        if (!m) { errno = ENOMEM; return NULL; }
        m->data = data; m->size = size; m->pos = 0; m->error = 0; m->eof = (size==0);
        OsFile_Handle h = from_memfile(m);
        return h;
    }

    OsFile_Handle real = __real_OsFile_open(filename, mode);
    return real;
}

extern "C" int __wrap_OsFile_exists(const jchar* filename) {
    if (!filename) return 0;
    if (is_section_uri_jchar(filename)) {
        const uint8_t* d; size_t s;
        int ok = resolve_section_uri_jchar(filename, &d, &s) ? 1 : 0;
        return ok;
    }
    int res = __real_OsFile_exists(filename);
    return res;
}

extern "C" int __wrap_OsFile_remove(const jchar* filename) {
    if (!filename) return -1;
    if (is_section_uri_jchar(filename)) {
        errno = EPERM;
        return -1;
    }
    return __real_OsFile_remove(filename);
}

extern "C" int __wrap_OsFile_rename(const jchar* oldname, const jchar* newname) {
    if (!oldname || !newname) return -1;
    if (is_section_uri_jchar(oldname) || is_section_uri_jchar(newname)) {
        errno = EPERM;
        return -1;
    }
    int r = __real_OsFile_rename(oldname, newname);
    return r;
}

extern "C" int __wrap_OsFile_close(OsFile_Handle h) {
    if (h == NULL) return 0;
    if (is_memfile_handle(h)) {
        free(to_memfile(h));
        return 0;
    }
    return __real_OsFile_close(h);
}

extern "C" int __wrap_OsFile_flush(OsFile_Handle h) {
    if (is_memfile_handle(h)) return 0;
    return __real_OsFile_flush(h);
}

// Хелпер: аккуратный дамп первых max_bytes из буфера
static void hex_dump_log(const void* p, size_t len, size_t max_bytes, const char* prefix) {
    const unsigned char* b = (const unsigned char*)p;
    size_t n = (len < max_bytes) ? len : max_bytes;
    char line[3*16 + 1];

    for (size_t i = 0; i < n; i += 16) {
        size_t row = (n - i) < 16 ? (n - i) : 16;
        char* out = line;
        for (size_t j = 0; j < row; ++j) {
            unsigned v = b[i + j];
            *out++ = "0123456789ABCDEF"[v >> 4];
            *out++ = "0123456789ABCDEF"[v & 15];
            *out++ = ' ';
        }
        *out = '\0';
        log_pe("%s +0x%04zX: %s", prefix ? prefix : "", i, line);
    }
    if (n < len) log_pe("%s ... (%zu bytes truncated)", prefix ? prefix : "", len - n);
}

extern "C" int __wrap_OsFile_read(OsFile_Handle h, void* buf, size_t size, size_t count) {

    if (!buf || count == 0) {
        return 0;
    }

    if (is_memfile_handle(h)) {
        MemFile* m = to_memfile(h);
        if (!m) {
            return 0;
        }
        if (m->pos >= m->size) {
            m->eof = 1;
            return 0;
        }

        size_t want;
        if (size != 0 && count > (SIZE_MAX / size)) {
            want = SIZE_MAX; // saturate
        } else {
            want = count * size;
        }

        size_t left = m->size - m->pos;
        size_t n = (want < left) ? want : left;

        if (n == 0) {
            m->eof = 1;
            return 0;
        }

        const unsigned char* src = m->data + m->pos;

        memcpy(buf, src, n);
        hex_dump_log(buf, n, 64, "    dst");

        size_t oldpos = m->pos;
        m->pos += n;
        if (m->pos >= m->size) m->eof = 1;

        return (int)n;
    }

    int r = __real_OsFile_read(h, buf, size, count);
    return r;
}

extern "C" int __wrap_OsFile_write(OsFile_Handle h, const void* buf, size_t size, size_t count) {
    if (is_memfile_handle(h)) {
        errno = EPERM;
        return -1;
    }
    return __real_OsFile_write(h, buf, size, count);
}

extern "C" long __wrap_OsFile_length(OsFile_Handle h) {
    if (is_memfile_handle(h)) {
        long len = (long)to_memfile(h)->size;
        return len;
    }
    return __real_OsFile_length(h);
}

extern "C" int __wrap_OsFile_seek(OsFile_Handle h, long offset, int whence) {
    if (is_memfile_handle(h)) {
        MemFile* m = to_memfile(h);
        size_t base = 0;
        switch (whence) {
            case SEEK_SET: base = 0; break;
            case SEEK_CUR: base = m->pos; break;
            case SEEK_END: base = m->size; break;
            default: m->error = EINVAL; errno = EINVAL; return -1;
        }
        long long npos = (long long)base + (long long)offset;
        if (npos < 0) npos = 0;
        if ((size_t)npos > m->size) npos = (long long)m->size;
        m->pos = (size_t)npos;
        m->eof = (m->pos >= m->size);
        return 0;
    }
    return __real_OsFile_seek(h, offset, whence);
}

extern "C" int __wrap_OsFile_error(OsFile_Handle h) {
    if (is_memfile_handle(h)) return to_memfile(h)->error != 0;
    return __real_OsFile_error(h);
}

extern "C" int __wrap_OsFile_eof(OsFile_Handle h) {
    if (is_memfile_handle(h)) return to_memfile(h)->eof;
    return __real_OsFile_eof(h);
}


extern "C" int __wrap_main(int argc, char **argv) {
  JVM_SetUseVerifier(false);
  JVM_SetConfig(JVM_CONFIG_HEAP_MINIMUM, 16 * 1024 * 1024);
  JVM_SetConfig(JVM_CONFIG_HEAP_CAPACITY, 16 * 1024 * 1024);
  JVM_Initialize();

  JVMSPI_SetSystemProperty("exe", *argv);
  
  char user_dir[256];
  GetCurrentDirectoryA(256, user_dir);
  JVMSPI_SetSystemProperty("user.dir", user_dir);

  // Ignore arg[0] -- the name of the program.
  argc --;
  argv ++;

  return JVM_Start((const JvmPathChar*)u"section://.java_00", "phoenix.image.Main", argc, argv);
}
