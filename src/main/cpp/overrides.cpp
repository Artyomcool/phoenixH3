extern "C" int strcmp(const char* a, const char* b);

extern "C" void* addressOfFunc(const char* name);

extern "C" void DBG(const char* format, ...);

 extern "C" void* __wrap__ZN2Os11loadLibraryEPKc(const char* libName) {
  DBG("loadLibrary: %s", libName);
    if (strcmp(libName, "phoenixH3") == 0) {
        return (void*)1;
    }
    return 0;
  }
  extern "C" void* __wrap__ZN2Os9getSymbolEPvPKc(void* handle, const char* name) {
  DBG("getSybmol: %s", name);
    int h = (int)handle;
    switch (h) {
    case 1:
        return addressOfFunc(name);
    }

    return 0;
  }
  