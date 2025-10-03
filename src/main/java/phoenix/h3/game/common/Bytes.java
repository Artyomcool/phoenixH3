package phoenix.h3.game.common;

public class Bytes {

    public static int indexOf(byte[] value, int valueCount, byte[] str, int strCount, int fromIndex) {
        byte first = str[0];
        int max = valueCount - strCount;

        for(int i = fromIndex; i <= max; ++i) {
            if (value[i] != first) {
                do {
                    ++i;
                } while(i <= max && value[i] != first);
            }

            if (i <= max) {
                int j = i + 1;
                int end = j + strCount - 1;

                for(int k = 1; j < end && value[j] == str[k]; ++k) {
                    ++j;
                }

                if (j == end) {
                    return i;
                }
            }
        }

        return -1;
    }

}
