
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

int memfs_add_ro(const char* name, const void* data, size_t size );

#ifdef __cplusplus
}
#endif