#include "jvm.h"
#include "file.h"
#include "jvmspi.h"
#include "jni.h"
#include "kni.h"
#include "sni.h"
#include "annotations.h"
#include "codegen.h"

#include <algorithm>
#include <cstdio>
#include <Windows.h>

extern "C" JNIEnv _jni_env;

#define PAGE_EXECUTE_READWRITE 0x40


int F_vsprintf(char* buffer, const char* format, va_list args) {
    return ((int(__cdecl*)(char*, const char*, va_list))0x6190DE)(buffer, format, args);
}

void* malloc(size_t num) {
    return ((void*(__cdecl*)(size_t))0x617492)(num);
}

void free(void* ptr) {
    if (ptr) ((void(__cdecl*)(void*))0x60B0F0)(ptr);
}

void* realloc(void* obj, size_t new_size) {
    return ((void*(__cdecl*)(void*, size_t))0x619890)(obj, new_size);
}

void* operator new(size_t sz) { return malloc(sz); }
void operator delete(void* p) noexcept { if (p) free(p); }
void* operator new[](size_t sz) { return malloc(sz); }
void operator delete[](void* p) noexcept { if (p) free(p); }
void operator delete(void* p, size_t) noexcept { if (p) free(p); }
void operator delete[](void* p, size_t) noexcept { if (p) free(p); }


inline void* o_GetModuleHandle(const char* name) {
    return ((void* (__stdcall*)(const char*))( *(void**)0x63A230 ))(name);
}

inline void* o_GetProcAddress(void* hModule, const char* name) {
    return ((void* (__stdcall*)(void*, const char*))( *(void**)0x63A22C ))(hModule, name);
}

typedef int (__stdcall *PFN_VirtualProtect)(void* addr, UINT32 size, UINT32 newProt, UINT32* oldProt);

int is_prefix_byte(UINT8 b) {
    return b == 0xF0 || b == 0xF2 || b == 0xF3 ||
           b == 0x2E || b == 0x36 || b == 0x3E || b == 0x26 ||
          (b >= 0x64 && b <= 0x67);
}

UINT32 x86_len(const UINT8* code) {
    const UINT8* p = code;
    const UINT8* start = p;
    int has_66 = 0, has_67 = 0;

    { int i;
      for (i = 0; i < 15; ++i) {
        UINT8 pr = *p;
        if (pr == 0xF0 || pr == 0xF2 || pr == 0xF3 ||
            pr == 0x2E || pr == 0x36 || pr == 0x3E || pr == 0x26 ||
           (pr >= 0x64 && pr <= 0x67)) {
            if (pr == 0x66) has_66 = 1;
            if (pr == 0x67) has_67 = 1;
            ++p; continue;
        }
        break;
      }
    }

    { UINT8 op = *p++;
      switch (op) {
        case 0x90: return (UINT32)(p - start);
        case 0xE8: case 0xE9: p += 4; return (UINT32)(p - start);
        case 0xEB: case 0xE3: p += 1; return (UINT32)(p - start);
        default: if (op >= 0x70 && op <= 0x7F) { p += 1; return (UINT32)(p - start); }
      }
      if (op == 0xC3 || op == 0xCB) return (UINT32)(p - start);
      if (op == 0xC2 || op == 0xCA) { p += 2; return (UINT32)(p - start); }
      if ((op >= 0x50 && op <= 0x5F) || (op >= 0x40 && op <= 0x47)) return (UINT32)(p - start);
      if (op == 0x6A) { p += 1; return (UINT32)(p - start); }
      if (op == 0x68) { p += 4; return (UINT32)(p - start); }
      if (op == 0xA0 || op == 0xA2) { p += has_67 ? 2 : 4; return (UINT32)(p - start); }
      if (op == 0xA1 || op == 0xA3) { p += has_67 ? 2 : 4; return (UINT32)(p - start); }
      if (op >= 0xB0 && op <= 0xB7) { p += 1; return (UINT32)(p - start); }
      if (op >= 0xB8 && op <= 0xBF) { p += has_66 ? 2 : 4; return (UINT32)(p - start); }
      if (op == 0x0F) {
          UINT8 op2 = *p++;
          if (op2 >= 0x80 && op2 <= 0x8F) { p += 4; return (UINT32)(p - start); }
          op = 0x0F;
      }

      { int expect_modrm = 0;
        if ((op <= 0x3F) || (op >= 0x62 && op <= 0x63) || (op >= 0x80 && op <= 0x8F) ||
            (op >= 0xC0 && op <= 0xC7) || (op >= 0xD0 && op <= 0xD3) ||
            (op >= 0xD8 && op <= 0xDF) || (op == 0xF6 || op == 0xF7) ||
            (op >= 0xFE && op <= 0xFF) || (op == 0x0F)) expect_modrm = 1;

        if (op == 0xA4 || op == 0xA5 || op == 0xAA || op == 0xAB || op == 0xAC || op == 0xAD)
            return (UINT32)(p - start);

        if (expect_modrm) {
            UINT8 modrm = *p++;
            UINT8 mod = (UINT8)((modrm >> 6) & 3);
            UINT8 reg = (UINT8)((modrm >> 3) & 7);
            UINT8 rm  = (UINT8)(modrm & 7);

            if (!has_67 && mod != 3 && rm == 4) {
                UINT8 sib = *p++;
                UINT8 base = (UINT8)(sib & 7);
                if (mod == 0 && base == 5) { p += 4; }
            }
            if (mod == 0) { if (!has_67 && rm == 5) p += 4; if (has_67 && rm == 6) p += 2; }
            else if (mod == 1) { p += 1; }
            else if (mod == 2) { p += has_67 ? 2 : 4; }

            if (op == 0x80) { p += 1; }
            else if (op == 0x81) { p += has_66 ? 2 : 4; }
            else if (op == 0x83) { p += 1; }
            else if (op == 0xC6) { p += 1; }
            else if (op == 0xC7) { p += has_66 ? 2 : 4; }
            else if (op == 0xF6 && reg == 0) { p += 1; }
            else if (op == 0xF7 && reg == 0) { p += has_66 ? 2 : 4; }
            else if (op == 0xC0 || op == 0xC1) { p += 1; }
        } else {
            if (op == 0xC8) { p += 3; }
            else if (op == 0xCC) { }
            else if (op == 0xCD) { p += 1; }
            else if (op == 0xA8) { p += 1; }
            else if (op == 0xA9) { p += has_66 ? 2 : 4; }
        }
      }
    }

    { UINT32 len = (UINT32)(p - start);
      if (len > 15) len = 15;
      return len;
    }
}

typedef enum { REL_NONE=0, REL_JMP_CALL32, REL_JMP8, REL_JCC8, REL_JCC32 } RelKind;

typedef struct {
    RelKind kind;
    UINT8   cond;
    UINT32  disp_off;
    UINT32  disp_size;
    UINT32  op_len;
    UINT32  prefix_len;
    UINT8   op1, op2;
} RelInfo;

RelInfo detect_rel(const UINT8* p) {
    RelInfo ri; const UINT8* q = p; UINT8 op, op2 = 0;
    memset(&ri, 0, sizeof(ri));
    while (is_prefix_byte(*q)) ++q;
    ri.prefix_len = (UINT32)(q - p);
    op = *q++; ri.op1 = op; ri.op_len = x86_len(p);

    if (op >= 0x70 && op <= 0x7F) { ri.kind=REL_JCC8; ri.cond=(UINT8)(op&0x0F); ri.disp_off=ri.prefix_len+1; ri.disp_size=1; return ri; }
    if (op == 0xEB) { ri.kind=REL_JMP8; ri.disp_off=ri.prefix_len+1; ri.disp_size=1; return ri; }
    if (op == 0xE8 || op == 0xE9) { ri.kind=REL_JMP_CALL32; ri.disp_off=ri.prefix_len+1; ri.disp_size=4; return ri; }
    if (op == 0x0F) { op2=*q++; ri.op2=op2;
        if (op2 >= 0x80 && op2 <= 0x8F) { ri.kind=REL_JCC32; ri.cond=(UINT8)(op2&0x0F); ri.disp_off=ri.prefix_len+2; ri.disp_size=4; return ri; }
    }
    ri.kind = REL_NONE; return ri;
}

void write_jmp_rel32(UINT8* at, const void* to) {
    UINT32 src_next = (UINT32)(at + 5);
    UINT32 dst = (UINT32)to;
    int rel = (int)(dst - src_next);
    at[0] = 0xE9;
    *(int*)(at + 1) = rel;
}
void write_call_rel32(UINT8* at, const void* to) {
    UINT32 src_next = (UINT32)(at + 5);
    UINT32 dst = (UINT32)to;
    int rel = (int)(dst - src_next);
    at[0] = 0xE8;
    *(int*)(at + 1) = rel;
}

UINT32 calc_stolen_len(const UINT8* src, UINT32 min_bytes) {
    UINT32 got = 0;
    while (got < min_bytes) {
        UINT32 l = x86_len(src + got);
        if (l == 0) break;
        got += l;
        if (got >= 15) break;
    }
    return got;
}

struct Trampoline {
    void* src;
    void* orig;
    UINT8 bridge[256];
    int orig_len;
    jobject global_ref = NULL;
    Trampoline* next = NULL;

    ~Trampoline() {
        free(orig);
        if (global_ref != NULL) {
            _jni_env.DeleteGlobalRef(global_ref);
        }
    }
};

int unlock(void* mem, int count) {
    PFN_VirtualProtect pVP; UINT32 oldProt=0;
    if (!count) return 0;

    void* hK32 = o_GetModuleHandle("Kernel32.dll");
    pVP  = (PFN_VirtualProtect)(UINT32)o_GetProcAddress(hK32, "VirtualProtect");
    pVP(mem, count, PAGE_EXECUTE_READWRITE, &oldProt);
    if (!pVP) return 0;
    return 1;
}

int build_trampoline(void* handler, Trampoline* tramp, int data, bool before) {
    UINT8* s = (UINT8*)tramp->src;
    tramp->orig_len = calc_stolen_len(s, 5);
    unlock(tramp->bridge, sizeof(tramp->bridge));

    UINT8* s_end = s + tramp->orig_len;
    UINT8* d = tramp->bridge;
    tramp->orig = malloc(tramp->orig_len);
    memcpy(tramp->orig, s, tramp->orig_len);
    char* data_ptr = (char*)(void*)&data;
    if (before) {
        *d++ = 0x9C;                               // pushfd 
        *d++ = 0x60;                               // pushad
        *d++ = 0x89; *d++ = 0xe2;                  // mov edx, esp
        *d++ = 0xB9; *d++ = *data_ptr++; *d++ = *data_ptr++; *d++ = *data_ptr++; *d++ = *data_ptr++; // mov ecx, imm
        write_call_rel32(d, handler); d += 5;
        *d++ = 0x85; *d++ = 0xC0;                  // test eax, eax
        *d++ = 0x74; *d++ = 0x0F;                  // je target  (skip 15 bytes on nonzero path)

        *d++ = 0x89; *d++ = 0x44; *d++ = 0x24; *d++ = 0x0C;  // mov dword ptr [esp+0x0C], eax   (ignored part of pushad)

        *d++ = 0x61;                                // popad
        *d++ = 0x9D;                                // popfd

        *d++ = 0x03; *d++ = 0x64; *d++ = 0x24; *d++ = 0xE8;  // add esp, [esp - 0x18]
        write_jmp_rel32(d, (UINT8*)tramp->src + tramp->orig_len); d += 5;

        // target (delta == 0):
        *d++ = 0x61;                                // popad
        *d++ = 0x9D;                                // popfd
    }

    while (s < s_end) {
        UINT32 ilen = x86_len(s);
        RelInfo ri; UINT32 s_inst_end; int disp = 0; UINT32 target;
        if (ilen == 0) { free(tramp->orig); return 0; }
        ri = detect_rel(s);
        if (ri.kind == REL_NONE) { memcpy(d, s, ilen); d += ilen; s += ilen; continue; }

        s_inst_end = (UINT32)(s + ilen);
        if (ri.disp_size == 1) { signed char d8 = *(signed char*)(s + ri.disp_off); disp = (int)d8; }
        else { disp = *(int*)(s + ri.disp_off); }
        target = s_inst_end + (UINT32)disp;

        if (ri.kind == REL_JMP_CALL32) {
            memcpy(d, s, ri.disp_off);
            *(int*)(d + ri.disp_off) = (int)(target - (UINT32)(d + ilen));
            d += ilen; s += ilen;
        } else if (ri.kind == REL_JMP8) {
            write_jmp_rel32(d, (void*)target);
            d += 5; s += ilen;
        } else if (ri.kind == REL_JCC8) {
            *d++ = 0x0F; *d++ = (UINT8)(0x80 | ri.cond);
            *(int*)d = (int)(target - (UINT32)(d + 4));
            d += 4; s += ilen;
        } else if (ri.kind == REL_JCC32) {
            memcpy(d, s, ri.disp_off);
            *(int*)(d + ri.disp_off) = (int)(target - (UINT32)(d + ilen));
            d += ilen; s += ilen;
        } else {
            memcpy(d, s, ilen); d += ilen; s += ilen;
        }
    }

    if (!before) {
        *d++ = 0x9C; /* pushfd */
        *d++ = 0x60; /* pushad */
        *d++ = 0x89; /* mov edx,*/
        *d++ = 0xe2; /* esp */
        *d++ = 0xB9; /* mov ecx, imm*/
        *d++ = *data_ptr++; // imm
        *d++ = *data_ptr++; // imm
        *d++ = *data_ptr++; // imm
        *d++ = *data_ptr++; // imm
        write_call_rel32(d, handler);
        d += 5;
        *d++ = 0x61; /* popfd */
        *d++ = 0x9D; /* popad */
    }

    write_jmp_rel32(d, (UINT8*)tramp->src + tramp->orig_len);
    d += 5;

    return 1;
}

Trampoline* install_inline_hook(void* target, void* handler, int data, bool before) {
    if (!target || !handler) return 0;
    Trampoline* t = new Trampoline();
    t->src = target;
    build_trampoline(handler, t, data, before);
    unlock(target, t->orig_len);

    write_jmp_rel32((UINT8*)target, &t->bridge);
    for (int i = 5; i < t->orig_len; ++i) ((UINT8*)target)[i] = 0x90;

    return t;
}

int put_patch(void* to, void* from, int count) {
    unlock(to, count);
    memcpy(to, from, count);
    return 1;
}

void remove_inline_hook(Trampoline* hk) {
    put_patch(hk->src, hk->orig, hk->orig_len);
    delete hk;
}

bool PASSWORD_CORRECT = false;


void print(const char* msg) {
    //showMessage(msg, NH3Dlg::TextColor::REGULAR);
}

#pragma pack(push,1)
typedef struct PushadRegs {
    UINT32 EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI;
} PushadRegs;
#pragma pack(pop)

typedef void* HANDLE;
typedef unsigned long DWORD;
typedef int BOOL;
typedef const char* LPCSTR;
typedef void* LPVOID;

#define FILE_SHARE_WRITE      0x00000002
#define OPEN_EXISTING         3
#define FILE_ATTRIBUTE_NORMAL 0x00000080

static BOOL   (__stdcall *pAllocConsole)(void) = 0;
static HANDLE (__stdcall *pCreateFileA)(LPCSTR,DWORD,DWORD,LPVOID,DWORD,DWORD,HANDLE) = 0;
static BOOL   (__stdcall *pWriteFile)(HANDLE,const void*,DWORD,DWORD*,LPVOID) = 0;
static BOOL   (__stdcall *pCloseHandle)(HANDLE) = 0;

static HANDLE g_con = 0;

static DWORD c_len(LPCSTR s){ DWORD n=0; while (s && s[n]) ++n; return n; }

// --- resolve needed WinAPI once
static void log_resolve_once() {
    static int inited = 0; if (inited) return; inited = 1;
    HMODULE k32 = (HMODULE)o_GetModuleHandle("kernel32.dll");
    pAllocConsole = (BOOL (__stdcall*)(void))o_GetProcAddress(k32, "AllocConsole");
    pCreateFileA  = (HANDLE(__stdcall*)(LPCSTR,DWORD,DWORD,LPVOID,DWORD,DWORD,HANDLE))o_GetProcAddress(k32,"CreateFileA");
    pWriteFile    = (BOOL  (__stdcall*)(HANDLE,const void*,DWORD,DWORD*,LPVOID))    o_GetProcAddress(k32,"WriteFile");
    pCloseHandle  = (BOOL  (__stdcall*)(HANDLE))                                   o_GetProcAddress(k32,"CloseHandle");
}

// --- public API
static void log_init() {
    log_resolve_once();
    if (!pAllocConsole || !pCreateFileA) return;
    if (!pAllocConsole()) return;
    g_con = pCreateFileA("CONOUT$", GENERIC_WRITE, FILE_SHARE_WRITE, 0,
                         OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
}

static void log_line(const char* s) {
    if (!g_con || g_con==INVALID_HANDLE_VALUE || !pWriteFile) return;
    DWORD w;
    pWriteFile(g_con, s, c_len(s), &w, 0);
    pWriteFile(g_con, "\r\n", 2, &w, 0);
}

static void log_shutdown() {
    if (g_con && g_con!=INVALID_HANDLE_VALUE && pCloseHandle) pCloseHandle(g_con);
    g_con = 0;
}

extern "C" {
void DBG(const char* format, ...)
{
    char b[2048];
    va_list args;
    va_start(args, format);
    F_vsprintf(b, format, args);
    va_end(args);
    
    log_line(b);
}
}

int downcall_address = -1;
int downcall_argc = 0;
int downcall_type = 0;
int downcall_args[1024];
int downcall_result;
int downcall_args_ptr = 0;

bool initialized = false;
int upcall = 0;
int leftOnUpcall = -1;
jint result;

void* _all_data_pos;



extern "C" bool has_last_handle();
extern "C" int first_method_index(jclass clazz, const char* method_name);
extern "C" int methods_args_count(jclass clazz, int method_index);
extern "C" int activation_method_index(jclass clazz, const char* name, const char* signature);
extern "C" jobject new_entry_activation_api(JNIEnv* jni, jclass clazz, int method_index);
extern "C" void bind_entry_int(jobject entry, int i, jint v);
extern "C" void bind_entry_long(jobject entry, int i, jlong v);
extern "C" void bind_entry_float(jobject entry, int i, jfloat v);
extern "C" void bind_entry_double(jobject entry, int i, jdouble v);
extern "C" void bind_entry_obj(jobject entry, int i, jobject v);
extern "C" void invoke_entry(jobject e);
extern "C" void entry_result_int(jobject entry);
extern "C" void* get_object_first_field_address(jobject objectHandle);
extern "C" jarray annotations_for_method(jclass clazz, int method_index);
extern "C" void* get_parameter_address();
extern "C" void resume_thread_no_unblock(JVMSPI_ThreadID thread_id);
extern "C" jint thread_stack_pointer();
extern "C" void set_thread_stack_pointer(jint value);
extern "C" void set_thread_result(jint value);
extern "C" jint class_methods_count(jclass clazz);
extern "C" char* className(jclass clazz);

extern "C" void slave_mode_yield();
extern "C" void* array_start_address(jarray src);
extern "C" int java_sp(JVMSPI_ThreadID thread_id);
extern "C" void pop_frame();

jclass h3Class;
jfieldID epResultField;

__attribute__((stdcall)) __attribute__((dllexport))
void SodSuperPatchEntryPoint(void* all_data_pos, PushadRegs* registers) {
    log_init();
    DBG("SodSuperPatchEntryPoint called");
    _all_data_pos = all_data_pos;

    char* path = "/embedded/cp.jar";
    int len = strlen(path);
    JvmPathChar cp[len + 1];
    for (int i = 0; i < len; i++) {
        cp[i] = path[i];
    }
    cp[len] = 0;

    UINT32* data_table = (UINT32*)all_data_pos;
    UINT32 total_size = *data_table;
    UINT32 files_count = *(data_table + 1);
    UINT32 dll_offset = *(data_table + 2);
    UINT32 jar_offset = *(data_table + 3);
    UINT32 patch_offset = *(data_table + 4);
    UINT32 end_offset = *(data_table + 5);

    void* base = (void*)(data_table + 1);

    void* jar = (void*)((char*)base + jar_offset);
        
    memfs_add_ro(path, jar, patch_offset - jar_offset);

    JVM_SetConfig(JVM_CONFIG_SLAVE_MODE, KNI_TRUE);
    JVM_SetUseVerifier(false);
    JVM_Initialize();
    JVM_Start(cp, "phoenix.h3.H3", 0, NULL);

    while (!initialized) {
        jlong r = JVM_TimeSlice();
        if (r == -2) {
            DBG("AFTER MAIN FINISHED??");
            JVM_CleanUp();
            break;
        }
        if (r == -1) {
            DBG("Can't make upcall, it's waiting for something))");
            JVM_CleanUp();
            break;
        }
        if (r > 0) {
            DBG("Sleep in upcall? Nope, I don't think so! %x", r);
            JVM_CleanUp();
            break;
        }
    }
    _jni_env.PushLocalFrame(1);
    h3Class = _jni_env.NewGlobalRef(_jni_env.FindClass("phoenix/h3/H3"));
    DBG("h3Class %p", h3Class);
    _jni_env.PopLocalFrame(NULL);

    DBG("SodSuperPatchEntryPoint initialized");
}

// __cdecl: caller restores ESP
int call_cdecl_raw_intel(void* fn, const void* stack_blob, int stack_len) {
    int r;
    __asm__ __volatile__(
        ".intel_syntax noprefix\n\t"
        "cld\n\t"                      // DF = 0
        "sub  esp, ecx\n\t"            // reserve stack space
        "mov  edi, esp\n\t"            // EDI = dst
        "mov  ebx, ecx\n\t"            // keep len across call (callee-saved)
        "rep  movsb\n\t"               // memcpy(esp, src, len)
        "call %[fn]\n\t"               // call fn; return in EAX
        "add  esp, ebx\n\t"            // restore ESP (caller-clean)
        ".att_syntax prefix\n\t"
        : "=a"(r)                      // 0: return in EAX
        : [fn] "r"(fn),
          "S"(stack_blob),             // ESI = src
          "c"(stack_len)               // ECX = len (for rep movsb)
        : "edi","ebx","memory","cc"
    );
    return r;
}

// generic regs: ECX/EDX provided, callee pops stack args (thiscall/fastcall/stdcall-like)
int call_regs_raw_intel(void* fn, int ecx_val, int edx_val,
                               const void* stack_blob, int stack_len) {
    int r;
    __asm__ __volatile__(
        ".intel_syntax noprefix\n\t"
        "cld\n\t"                      // forward MOVS
        "sub  esp, ecx\n\t"            // reserve space
        "mov  edi, esp\n\t"            // EDI = dst
        "rep  movsb\n\t"               // memcpy(esp, src, len)
        "mov  ecx, %[ecxv]\n\t"        // set ECX for the call
        "call %[fn]\n\t"               // indirect call
        ".att_syntax prefix\n\t"
        : "=a"(r)
        : [fn]   "r"(fn),
          [ecxv] "r"(ecx_val),
          "S"(stack_blob),             // ESI = src
          "c"(stack_len),              // ECX = len (for rep movsb)
          "d"(edx_val)                 // EDX = edx_val (for call)
        : "edi", "memory", "cc"
    );
    return r;
}

Trampoline* trampoline = NULL;

int downcall_depth = 0;
int downcall_stack_pointer[1024];
int lastGuardSignal = -1;
int lastPushedGuard = -1;
bool DONE = false;

void cleanup() {
    DONE = true;
    while (trampoline != NULL) {
        Trampoline* next = trampoline->next;
        remove_inline_hook(trampoline);
        trampoline = next;
    }
    JVM_CleanUp();
}

__attribute__((__fastcall__))
int JVMPatchHook(int data, int stack_pointer) {
    _jni_env.PushLocalFrame(32);
    bool hasLoop = upcall == leftOnUpcall;
    int u = upcall++;
    
    _jni_env.SetStaticIntField(h3Class, _jni_env.GetStaticFieldID(h3Class, "depth", "I"), upcall);
    if (!hasLoop) {
        int method = activation_method_index(h3Class, "loop", "()V");
        jobject entry = new_entry_activation_api(&_jni_env, h3Class, method);
        invoke_entry(entry);
    }

    {int method = activation_method_index(h3Class, "exitFromUpcall", "(I)V");
    jobject entry = new_entry_activation_api(&_jni_env, h3Class, method);
    bind_entry_int(entry, 0, u);
    invoke_entry(entry);}
    
    
    {
        auto fn = (codegen::CreateEntryFunc)(void*)data;
        jobject entry = fn(stack_pointer);
        invoke_entry(entry);
    }

    while (upcall != u) {
        if (DONE) {
            return 0;
        }
        jlong r = JVM_TimeSlice();
        if (DONE) {
            return 0;
        }
        if (downcall_address != -1) {
            int t = downcall_type;
            downcall_type = 0;
            void* address = (void*)downcall_address;
            downcall_address = -1;
            int ecx =  downcall_args[0];
            int edx =  downcall_args[1];
            switch (t) {
            case 0: // stdcall
                downcall_result = call_regs_raw_intel(address, 0, 0, &downcall_args[0], downcall_argc * 4);
                break;
            case 1: // vtable
                {
                    int offset = (int)address;
                    int* vtable = (int*)(void*)*(int*)ecx;
                    void* method = (void*)*(vtable + offset);
                    downcall_result = call_regs_raw_intel(method, ecx, 0, &downcall_args[1], (downcall_argc - 1) * 4);
                }
                break;
            case 2: // cdecl
                downcall_result = call_cdecl_raw_intel(address, &downcall_args[0], downcall_argc * 4);
                break;
            case 3: // thiscall
                downcall_result = call_regs_raw_intel(address, ecx, 0, &downcall_args[1], (downcall_argc - 1) * 4);
                break;
            case 4: // fastcall
                downcall_result = call_regs_raw_intel(address, ecx, edx, &downcall_args[2], (downcall_argc - 2) * 4);
                break;
            }
            continue;
        }
        if (r == -2) {
            DBG("AFTER MAIN FINISHED??");
            cleanup();
            return 0;
        }
        if (r == -1) {
            DBG("Can't make upcall, it's waiting for something))");
            cleanup();
            return 0;
        }
        if (r > 0) {
            DBG("Sleep in upcall? Nope, I don't think so! %x", r);
            cleanup();
            return 0;
        }
    }
    _jni_env.SetStaticIntField(h3Class, _jni_env.GetStaticFieldID(h3Class, "depth", "I"), upcall);

    leftOnUpcall = upcall;
    //int result = _jni_env.GetStaticIntField(epClass, epResultField);
    _jni_env.PopLocalFrame(NULL);
    return 0;//result;
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(H3_notifyInitialized) {
    slave_mode_yield();
    initialized = true;
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(H3_pauseJvmLoop) {
    slave_mode_yield();
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(H3_extractPatchInfo) {
    KNI_StartHandles(1);
    KNI_DeclareHandle(patchInfo);

    KNI_GetParameterAsObject(1, patchInfo);


    UINT32* data_table = (UINT32*)_all_data_pos;
    UINT32 total_size = *data_table;
    UINT32 files_count = *(data_table + 1);
    UINT32 dll_offset = *(data_table + 2);
    UINT32 jar_offset = *(data_table + 3);
    UINT32 patch_offset = *(data_table + 4);
    UINT32 end_offset = *(data_table + 5);

    void* base = (void*)(data_table + 1);

    void* jar = (void*)((char*)base + jar_offset);

    int* addr = (int*)get_object_first_field_address(patchInfo);
    addr[0] = (int)_all_data_pos;
    addr[1] = total_size + 4; // include size header itself
    addr[2] = patch_offset + (int)base;
    addr[3] = end_offset - patch_offset; 
    
    KNI_EndHandles();
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(H3_exitFromUpcall) {
    upcall = KNI_GetParameterAsInt(1);
    KNI_ReturnVoid();
}


KNIEXPORT KNI_RETURNTYPE_INT
KNIDECL(H3_exitFromDowncall) {
    KNI_ReturnInt(downcall_result);
}

KNIEXPORT KNI_RETURNTYPE_INT
KNIDECL(Memory_malloc) {
    jint size = KNI_GetParameterAsInt(1);
    KNI_ReturnInt((jint)malloc(size));
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Memory_free) {
    jint ptr = KNI_GetParameterAsInt(1);
    free((void*)ptr);
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Memory_memcpy) {
    jint dst = KNI_GetParameterAsInt(1);
    jint src = KNI_GetParameterAsInt(2);
    jint amount = KNI_GetParameterAsInt(3);
    memcpy((void*)dst, (void*)src, amount);
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_INT
KNIDECL(Memory_byteAt) {
    jint address = KNI_GetParameterAsInt(1);
    KNI_ReturnInt(*(char*)(void*)address);
}

KNIEXPORT KNI_RETURNTYPE_INT
KNIDECL(Memory_dwordAt) {
    jint address = KNI_GetParameterAsInt(1);
    KNI_ReturnInt(*(int*)(void*)address);
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Memory_arrayAt) {
    KNI_StartHandles(1);
    KNI_DeclareHandle(data);

    jint address = KNI_GetParameterAsInt(1);
    KNI_GetParameterAsObject(2, data);  // array
    jint start = KNI_GetParameterAsInt(3);
    jint length = KNI_GetParameterAsInt(4);

    char* to = ((char*)array_start_address(data)) + start;
    char* from = (char*)(void*)address;
    memcpy(to, from, length);
    
    KNI_EndHandles();
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Memory_putByte) {
    jint address = KNI_GetParameterAsInt(1);
    jint value = KNI_GetParameterAsInt(2);
    *(char*)(void*)address = (char)value;
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Memory_putDword) {
    jint address = KNI_GetParameterAsInt(1);
    jint value = KNI_GetParameterAsInt(2);
    *(int*)(void*)address = value;
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Memory_putArray) {
    KNI_StartHandles(1);
    KNI_DeclareHandle(data);

    jint address = KNI_GetParameterAsInt(1);
    KNI_GetParameterAsObject(2, data);  // byte[]
    jint start = KNI_GetParameterAsInt(3);
    jint length = KNI_GetParameterAsInt(4);

    char* from = ((char*)array_start_address(data)) + start;
    char* to = (char*)(void*)address;
    memcpy(to, from, length);
    
    KNI_EndHandles();
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_OBJECT
KNIDECL(Memory_cstrAt) {
    KNI_StartHandles(1);
    KNI_DeclareHandle(retStr);
    jint address = KNI_GetParameterAsInt(1);
    jint maxLength = KNI_GetParameterAsInt(2);
    const char* src = (const char*)(intptr_t)address;
    jint n = 0;
    while (n < maxLength && src[n] != 0) ++n;
    char* buf = (char*)malloc((size_t)n + 1);
    if (buf) {
        memcpy(buf, src, (size_t)n);
        buf[n] = 0;
        KNI_NewStringUTF(buf, retStr);
        free(buf);
    } else {
        KNI_NewStringUTF("", retStr);
    }
    KNI_EndHandlesAndReturnObject(retStr);
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Memory_putCstr) {
    KNI_StartHandles(1);
    KNI_DeclareHandle(text);

    jint address = KNI_GetParameterAsInt(1);
    KNI_GetParameterAsObject(2, text);

    jsize u16len = KNI_GetStringLength(text);
    jchar* jbuf = (jchar*)malloc((size_t)u16len * sizeof(jchar));
    if (jbuf) {
        KNI_GetStringRegion(text, 0, u16len, jbuf);
        char* dst = (char*)(intptr_t)address;
        for (jsize i = 0; i < u16len; ++i) dst[i] = (char)(jbuf[i] & 0x7F);
        dst[u16len] = '\0';
        free(jbuf);
    }

    KNI_EndHandles();
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Patcher_performPatchInstallation) {
    KNI_StartHandles(2);
    KNI_DeclareHandle(patcher);
    KNI_DeclareHandle(patcherClass);

    KNI_GetParameterAsObject(1, patcher);
    KNI_GetObjectClass(patcher, patcherClass);
    
    _jni_env.PushLocalFrame(16);
    int method_count = class_methods_count(patcherClass);
    for (int i = 0; i < method_count; i++) {
        jobjectArray annotations = annotations_for_method(patcherClass, i);
        if (annotations == NULL) {
            continue;
        }
        
        jbyteArray method_annotations = _jni_env.GetObjectArrayElement(annotations, 0);
        if (!method_annotations) {
            continue;
        }

        annotations::UpcallAnn upcall;
        bool hasUpcall;
        {
            int size = _jni_env.GetArrayLength(method_annotations);
            jbyte* data = new jbyte[size];
            _jni_env.GetByteArrayRegion(method_annotations, 0, size, data);
            hasUpcall = annotations::parseUpcallAnnotation(patcherClass, (unsigned char*)data, size, upcall);
            delete[] data;
        }

        if (!hasUpcall) {
            continue;
        }

        jbyteArray param_annotations = _jni_env.GetObjectArrayElement(annotations, 1);
        annotations::Array<annotations::ParamAnn> params;
        if (param_annotations) {
            int size = _jni_env.GetArrayLength(param_annotations);
            jbyte* data = new jbyte[size];
            _jni_env.GetByteArrayRegion(param_annotations, 0, size, (jbyte*)data);
            params = annotations::parseParamAnnArray(patcherClass, (unsigned char*)data, size);
            delete[] data;
        }


        jobject globalPatcher = _jni_env.NewGlobalRef(patcher);
        void* data = (void*)codegen::emit_bind_params_fastcall(globalPatcher, i, params);
        DBG("Installed: %s/%d", className(patcherClass));
        //todo - before/after
        auto* t = install_inline_hook((void*)(upcall.base + upcall.offset), (void*)&JVMPatchHook, (int)data, true);
        t->next = trampoline;
        trampoline = t;
    }

    _jni_env.PopLocalFrame(NULL);
    KNI_EndHandles();
    DBG("after patch installation");
    KNI_ReturnVoid();
}

KNIEXPORT KNI_RETURNTYPE_VOID
KNIDECL(Patcher_uninstallPatches) {
    KNI_StartHandles(1);
    KNI_DeclareHandle(patcher);
    KNI_GetParameterAsObject(1, patcher);
    // TODO
    KNI_EndHandles();
    KNI_ReturnVoid();
}

int __stdcall push_downcall(int target_address, int argc, int type) {
    downcall_address = target_address;
    downcall_type = type;
    downcall_argc = argc;
    for (int i = 0; i < argc; i++) {
        downcall_args[i] = KNI_GetParameterAsInt(1 + i);
    }
    slave_mode_yield();
    
    _jni_env.PushLocalFrame(8);
    
    int method = activation_method_index(h3Class, "exitFromDowncall", "()I");
    jobject entry = new_entry_activation_api(&_jni_env, h3Class, method);
    entry_result_int(entry);
    invoke_entry(entry);
    
    _jni_env.PopLocalFrame(NULL);
    return 0x99;
}

void* downcallFor(const char* class_sig, const char* method_name) {
    _jni_env.PushLocalFrame(8);

    jclass clazz = _jni_env.FindClass(class_sig);
    if (!clazz) {
        _jni_env.PopLocalFrame(NULL);
        return 0;
    }

    int method_index = first_method_index(clazz, method_name);
    jobjectArray annotations = annotations_for_method(clazz, method_index);
    if (!annotations) {
        _jni_env.PopLocalFrame(NULL);
        return 0;
    }

    jbyteArray method_annotations = _jni_env.GetObjectArrayElement(annotations, 0);
    if (!method_annotations) {
        _jni_env.PopLocalFrame(NULL);
        return 0;
    }

    int size = _jni_env.GetArrayLength(method_annotations);
    unsigned char* data = new unsigned char[size];
    _jni_env.GetByteArrayRegion(method_annotations, 0, size, (jbyte*)data);
    annotations::DowncallAnn downcall;
    bool hasDowncall = annotations::parseDowncallAnnotation(clazz, data, size, downcall);
    delete[] data;

    if (!hasDowncall) {
        _jni_env.PopLocalFrame(NULL);
        return 0;
    }

    int argc = methods_args_count(clazz, method_index);
    DBG("after methods_args_count %d", argc);

    _jni_env.PopLocalFrame(NULL);
    return codegen::make_generated_function(downcall.value, argc, downcall.cc, (void*)&push_downcall);
}

static int hex4(const char* p) {
    int v = 0;
    for (int k = 0; k < 4; ++k) {
        char c = p[k];
        v <<= 4;
        if (c >= '0' && c <= '9') v |= (c - '0');
        else if (c >= 'a' && c <= 'f') v |= (c - 'a' + 10);
        else if (c >= 'A' && c <= 'F') v |= (c - 'A' + 10);
        else return -1;
    }
    return v;
}

#define NATIVE_CLASS_BEGIN(PATH, CLASS) \
    if (strcmp(class_sig, PATH "/" #CLASS) == 0) {

#define NATIVE_CLASS_END \
    }

// одна строка метода (важно: strcmp(...) == 0)
#define NATIVE_METHOD(JC, name) \
    if (strcmp(method_name, #name) == 0) return (void*)&Java_##JC##_##name;

extern "C" void* addressOfFunc(const char* name) {
    DBG("addressOfFunc: %s", name);
    if (!name) return 0;
    if (name[0] != 'J' || name[1] != 'a' || name[2] != 'v' || name[3] != 'a' || name[4] != '_') return 0;
    // Parse the JNI-style name: Java_package1_package2_Class_method__sig
    // Extract class signature and method name separately
    const char* p = name + 5; // skip "Java_"
    static char class_sig[512];
    int i = 0, j = 0;

    int last_underscore = -1;
    // Copy class part, replacing '_' with '/' except for "_1" (which is '_')
    while (*p) {
        if (*p == '_') {
            if (p[1] == '1') {             // '_' -> '_'
                class_sig[i++] = '_';
                p += 2;
                continue;
            }
            if (p[1] == '2') {             // ';' -> ';'
                class_sig[i++] = ';';
                p += 2;
                continue;
            }
            if (p[1] == '3') {             // '[' -> '['
                class_sig[i++] = '[';
                p += 2;
                continue;
            }
            if (p[1] == '0') {
                int code = hex4(p + 2);
                if (code >= 0) {
                    class_sig[i++] = (char)code;
                    p += 6; // '_' '0' + 4 hex
                    continue;
                }
            }

            last_underscore = i;
            class_sig[i++] = '/';
            ++p;
        } else {
            class_sig[i++] = *p++;
        }
    }
    class_sig[i] = 0;
    class_sig[last_underscore] = 0;
    char* method_name = &class_sig[last_underscore + 1];

    // Now class_sig contains the class signature, method_name contains the method name
    // Example: Java_phoenix_h3_H3_notifyInitialized -> class_sig="phoenix/h3/H3", method_name="notifyInitialized"
    DBG("Resolved: class_sig=%s, method_name=%s", class_sig, method_name);
    void* downcall = downcallFor(class_sig, method_name);
    if (downcall) return downcall;

    NATIVE_CLASS_BEGIN("phoenix/h3", H3)
        NATIVE_METHOD(H3, notifyInitialized)
        NATIVE_METHOD(H3, exitFromUpcall)
        NATIVE_METHOD(H3, exitFromDowncall)
        NATIVE_METHOD(H3, extractPatchInfo)
        NATIVE_METHOD(H3, pauseJvmLoop)
    NATIVE_CLASS_END

    NATIVE_CLASS_BEGIN("phoenix/h3/game/patch", Patcher)
        NATIVE_METHOD(Patcher, performPatchInstallation)
        NATIVE_METHOD(Patcher, uninstallPatches)
    NATIVE_CLASS_END

    NATIVE_CLASS_BEGIN("phoenix/h3/game/stdlib", Memory)
        NATIVE_METHOD(Memory, malloc)
        NATIVE_METHOD(Memory, free)
        NATIVE_METHOD(Memory, memcpy)
        NATIVE_METHOD(Memory, byteAt)
        NATIVE_METHOD(Memory, dwordAt)
        NATIVE_METHOD(Memory, arrayAt)
        NATIVE_METHOD(Memory, putByte)
        NATIVE_METHOD(Memory, putDword)
        NATIVE_METHOD(Memory, putArray)
        NATIVE_METHOD(Memory, cstrAt)
        NATIVE_METHOD(Memory, putCstr)
    NATIVE_CLASS_END


    return 0;
}


jboolean JVMSPI_CheckExit(void) {
  return KNI_TRUE;
}

struct SystemProperty {
   SystemProperty * next;
   char *name;
   char *value;
};

static SystemProperty * system_properties = NULL;

void JVMSPI_SetSystemProperty(const char *property_name, const char *property_value) {
}

char *JVMSPI_GetSystemProperty(const char *property_name) {
  return NULL;
}

void JVMSPI_FreeSystemProperty(const char * /*prop_value*/) {
}

void JVMSPI_DisplayUsage(char* message) {
}

int JVMSPI_HandleUncaughtException(const int isolate_id,
                                   const char * exception_class_name,
                                   const int exception_class_name_length,
                                   const char * message,
                                   const int flags,
                                   int * exit_code) {
    DBG("JVMSPI_HandleUncaughtException");
    DBG("%s", exception_class_name);
    DBG("%s", message);
  return JVMSPI_IGNORE;
}

int JVMSPI_HandleOutOfMemory(const int isolate_id,
                             const int limit,
                             const int reserve,
                             const int available,
                             const int alloc_size,
                             const int flags,
                             int * exit_code) {
    DBG("JVMSPI_HandleOutOfMemory");
  return JVMSPI_IGNORE;
}

char buf[4096];
int buf_pos = 0;

void JVMSPI_PrintRaw(const char* s, int length) {
    for (int i = 0; i < length; i++) {
        if (s[i] == '\n' || buf_pos == 4095) {
            buf[buf_pos] = 0;
            log_line(buf);
            buf_pos = 0;
            if (s[i] == '\n') {
                continue;
            }
        }
        buf[buf_pos++] = s[i];
    }
}

void JVMSPI_Exit(int code) {
  DBG("We are leaving JVM");
  exit(code);
}

void JVMSPI_CheckEvents(JVMSPI_BlockedThreadInfo *blocked_threads,
                        int blocked_threads_count,
                        jlong timeout) {
  if (timeout > 0) {
    //Os::sleep(timeout);
  }
}

char* getenv(const char* name) {
    return NULL;
}

void exit(int status) {
    // todo?
    //DBG("EXIT %d", status);
}


KNIEXPORT KNI_RETURNTYPE_INT
Java_com_sun_cldc_io_j2me_socket_Protocol_open0() {
  KNI_ReturnInt(-1);
}

KNIEXPORT KNI_RETURNTYPE_INT
Java_com_sun_cldc_io_j2me_socket_Protocol_readBuf() {
  KNI_ReturnInt(-1);
}

KNIEXPORT KNI_RETURNTYPE_INT
Java_com_sun_cldc_io_j2me_socket_Protocol_readByte() {
  KNI_ReturnInt(-1);
}

KNIEXPORT KNI_RETURNTYPE_INT
Java_com_sun_cldc_io_j2me_socket_Protocol_writeBuf() {
  KNI_ReturnInt(-1);
}

KNIEXPORT KNI_RETURNTYPE_INT
Java_com_sun_cldc_io_j2me_socket_Protocol_writeByte() {
  KNI_ReturnInt(-1);
}

KNIEXPORT KNI_RETURNTYPE_INT
Java_com_sun_cldc_io_j2me_socket_Protocol_available0() {
  KNI_ReturnInt(0);
}

KNIEXPORT KNI_RETURNTYPE_VOID
Java_com_sun_cldc_io_j2me_socket_Protocol_close0() {
}
