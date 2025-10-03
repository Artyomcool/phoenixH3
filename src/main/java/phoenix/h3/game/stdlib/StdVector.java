package phoenix.h3.game.stdlib;

import static phoenix.h3.H3.dbg;
import static phoenix.h3.game.stdlib.Memory.*;

public class StdVector {

    public static final int OFFSET_DATA_PTR = 0x4;
    public static final int OFFSET_DATA_END_PTR = 0x8;
    public static final int OFFSET_DATA_CAPACITY_END_PTR = 0xC;

    public static int dataPtr(int vector) {
        return dwordAt(vector + OFFSET_DATA_PTR);
    }

    public static int dataEndPtr(int vector) {
        return dwordAt(vector + OFFSET_DATA_END_PTR);
    }

    public static int dataCapacityEndPtr(int vector) {
        return dwordAt(vector + OFFSET_DATA_CAPACITY_END_PTR);
    }

    public static void copy(int dst, int src) {
        int srcDataPtr = dataPtr(src);
        int srcDataEndPtr = dataEndPtr(src);
        int size = srcDataEndPtr - srcDataPtr;
        int dstMalloc = malloc(size);
        memcpy(dstMalloc, srcDataPtr, size);
        // todo optimize and deallocate
        putDword(dst + OFFSET_DATA_PTR, dstMalloc);
        putDword(dst + OFFSET_DATA_END_PTR, dstMalloc + size);
        putDword(dst + OFFSET_DATA_CAPACITY_END_PTR, dstMalloc + size);
    }

    public static int add(int vector, int sizeInBytes) {
        int dataPtr = dataPtr(vector);
        int dataEndPtr = dataEndPtr(vector);
        int dataCapacityPtr = dataCapacityEndPtr(vector);
        if (dataCapacityPtr - dataEndPtr >= sizeInBytes) {
            putDword(vector + OFFSET_DATA_END_PTR, dataEndPtr + sizeInBytes);
            return dataEndPtr;
        }

        int oldSize = dataEndPtr - dataPtr;
        int newSize = oldSize + sizeInBytes;
        int newDataPtr = malloc(newSize);
        memcpy(newDataPtr, dataCapacityPtr, oldSize);
        free(dataPtr);
        putDword(vector + OFFSET_DATA_PTR, newDataPtr);
        putDword(vector + OFFSET_DATA_END_PTR, newDataPtr + newSize);
        putDword(vector + OFFSET_DATA_CAPACITY_END_PTR, newDataPtr + newSize);

        dbg(oldSize, " ", newSize);

        return newDataPtr + oldSize;
    }

    public static int sizeInBytes(int vector) {
        return dataEndPtr(vector) - dataPtr(vector);
    }
}
