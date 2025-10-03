/* k32_proxies_full.c — x86 stdcall proxies (no includes/typedefs) */

inline void* o_GetModuleHandle(const char* name) {
    void* (__attribute__((stdcall)) *fn)(const char*) = (void* (__attribute__((stdcall)) *)(const char*))(*(int*)0x63A230);
    return fn(name);
}

inline void* o_GetProcAddress(void* hModule, const char* name) {
    void* (__attribute__((stdcall)) *fn)(void*, const char*) = (void* (__attribute__((stdcall)) *)(void*, const char*))(*(int*)0x63A22C);
    return fn(hModule, name);
}

static void* g_k32;

/* ---- макросы ---- */

/* return-valued proxy */
#define K32_PROXY_R(NAME, RET, ARGT, ARGS, BYTES)                                    \
  static void* p_##NAME;                                                             \
  RET __attribute__((stdcall)) NAME##_proxy ARGT asm("_" #NAME "@" #BYTES);          \
  RET __attribute__((stdcall)) NAME##_proxy ARGT {                                   \
    if (!p_##NAME) {                                                                 \
      if (!g_k32) {                                                                  \
        g_k32 = o_GetModuleHandle("kernel32.dll");                                   \
        if (!g_k32) g_k32 = o_GetModuleHandle("KERNEL32.DLL");                       \
      }                                                                              \
      if (g_k32) p_##NAME = o_GetProcAddress(g_k32, #NAME);                          \
      if (!p_##NAME) return (RET)0;                                                  \
    }                                                                                \
    return ((RET (__attribute__((stdcall))*) ARGT)(p_##NAME)) ARGS;                  \
  }                                                                                  \
  void* __imp_##NAME##_##BYTES asm("__imp__" #NAME "@" #BYTES) = (void*)NAME##_proxy;

/* void proxy */
#define K32_PROXY_V(NAME, ARGT, ARGS, BYTES)                                         \
  static void* p_##NAME;                                                             \
  void __attribute__((stdcall)) NAME##_proxy ARGT asm("_" #NAME "@" #BYTES);         \
  void __attribute__((stdcall)) NAME##_proxy ARGT {                                  \
    if (!p_##NAME) {                                                                 \
      if (!g_k32) {                                                                  \
        g_k32 = o_GetModuleHandle("kernel32.dll");                                   \
        if (!g_k32) g_k32 = o_GetModuleHandle("KERNEL32.DLL");                       \
      }                                                                              \
      if (g_k32) p_##NAME = o_GetProcAddress(g_k32, #NAME);                          \
      if (!p_##NAME) return;                                                         \
    }                                                                                \
    ((void (__attribute__((stdcall))*) ARGT)(p_##NAME)) ARGS;                        \
  }                                                                                  \
  void* __imp_##NAME##_##BYTES asm("__imp__" #NAME "@" #BYTES) = (void*)NAME##_proxy;

/* ---- список функций ---- */

K32_PROXY_R(CloseHandle,
    int,
    (void* h),
    (h),
    4)

K32_PROXY_R(CreateThread,
    void*,
    (void* sa, unsigned long stackSize, void* startRoutine, void* param, unsigned long flags, unsigned long* threadId),
    (sa, stackSize, startRoutine, param, flags, threadId),
    24)

K32_PROXY_R(FlushInstructionCache,
    int,
    (void* hProc, const void* addr, unsigned long size),
    (hProc, addr, size),
    12)

K32_PROXY_R(GetCurrentProcess,
    void*,
    (void),
    (),
    0)

K32_PROXY_V(GetSystemInfo,
    (void* pSysInfo),
    (pSysInfo),
    4)

K32_PROXY_V(GetSystemTimeAsFileTime,
    (void* pFileTime),
    (pFileTime),
    4)

K32_PROXY_R(QueryPerformanceCounter,
    int,
    (void* pLi),
    (pLi),
    4)

K32_PROXY_R(QueryPerformanceFrequency,
    int,
    (void* pLi),
    (pLi),
    4)

K32_PROXY_R(SetThreadPriority,
    int,
    (void* hThread, int priority),
    (hThread, priority),
    8)

K32_PROXY_V(Sleep,
    (unsigned long ms),
    (ms),
    4)

K32_PROXY_R(SystemTimeToFileTime,
    int,
    (const void* pSysTime, void* pFileTime),
    (pSysTime, pFileTime),
    8)

K32_PROXY_R(VirtualAlloc,
    void*,
    (void* addr, unsigned long size, unsigned long type, unsigned long protect),
    (addr, size, type, protect),
    16)

K32_PROXY_R(VirtualFree,
    int,
    (void* addr, unsigned long size, unsigned long freeType),
    (addr, size, freeType),
    12)

K32_PROXY_R(VirtualQuery,
    unsigned long,
    (const void* addr, void* mbi, unsigned long len),
    (addr, mbi, len),
    12)
