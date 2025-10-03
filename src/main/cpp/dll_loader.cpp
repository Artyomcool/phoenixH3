
using GzRead = int (__attribute__((thiscall)) *)(int _this, void* dst, int size);
using Malloc = void* (__attribute__((cdecl)) *)(int size);
using Memcpy = void* (__attribute__((cdecl)) *)(void* dst, const void* src, int n);
using EntryPoint = void* (__attribute__((stdcall)) *)(void* totalData, void* esp);

__attribute__((__fastcall__)) __attribute__((dllexport))
int LoadDll(int gz, int esp) {
    bool fromNewMap = esp < 0;
    if (fromNewMap) {
        esp = ~esp;
    }

    int gzVtable = *(int*)(void*)gz;
    GzRead read = (GzRead)*((int*)(void*)gzVtable + 1);
    Malloc malloc = (Malloc)0x617492;
    Memcpy memcpy = (Memcpy)0x617B50;

    int totalSize;
    read(gz, &totalSize, 4);

    void* totalData = malloc(totalSize + 4);
    *(int*)totalData = totalSize;
    
    void* totalDataWithoutSize =  (char*)totalData + 4;
    read(gz, totalDataWithoutSize, totalSize);  // this approach slows down save list loading significantly, store dll separately, after header's data

    if (!fromNewMap) {
        /*
            8 bytes - MAGIC (H3SVG...) -> drop
            4 bytes - version -> drop
            4 bytes - version -> drop
            32 bytes - ??? -> drop
        */
        // drop meaningless data to text buffer
        read(gz, (void*)0x697428, 8 + 4 + 4 + 32);
        
        int nextEsp = *(int*)(void*)(esp - 0x196E4C + 0x196F04);
        for (int i = 0; i < 5; i++) {
            int retAddr = *(int*)(void*)(nextEsp + 4);
            if (retAddr == 0x58300D) {
                return 0;
            }
            nextEsp = *(int*)(void*)nextEsp;
        }
        // todo check the primary case as well and go back as a default behaviour, not vice versa
    }

    int* totalDataTable = (int*)totalDataWithoutSize;
    int filesCount = *totalDataTable;
    int dllOffset = *(totalDataTable+1);
    int nextFileOffset = *(totalDataTable+2);
    int dllSize = nextFileOffset - dllOffset;

    void* dllCopy = malloc(dllSize);
    memcpy(dllCopy, (char*)totalDataWithoutSize + dllOffset, dllSize);  // free someday((

    int* dllTable = (int*)dllCopy;
    int relocationsCount = *dllTable++;
    char* dllStart = (char*)dllCopy + (relocationsCount + 2) * 4;

    for (int i = 0; i < relocationsCount; i++) {
        int offset = *dllTable++;
        *(int*)(dllStart + offset) += (int)(void*)dllStart;
    }

    int entryPointOffset = *dllTable;
    EntryPoint entryPoint = (EntryPoint)(void*)(dllStart + entryPointOffset);

    entryPoint(totalData, (void*)esp);
    return 0;
}

