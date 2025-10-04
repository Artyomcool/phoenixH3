package phoenix.h3.game.stdlib;

public class AutoGrowArray {

    public int[] array = new int[128];
    public int size = 0;

    public int add(int v) {
        if (array.length == size) {
            int[] newArray = new int[size * 2];
            System.arraycopy(array, 0, newArray, 0, size);
            array = newArray;
        }
        array[size++] = v;
        return v;
    }

}
