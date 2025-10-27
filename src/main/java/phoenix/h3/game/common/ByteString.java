package phoenix.h3.game.common;

public class ByteString {

    private byte[] data;
    private int hash;

    public ByteString() {
    }

    public ByteString(byte[] data) {
        with(data);
    }

    public ByteString with(byte[] data) {
        this.data = data;
        int h = 0;
        for (byte d : data) {
            h = (h << 5) - h + d;
        }
        return this;
    }

    public byte[] data() {
        return data;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        ByteString that = (ByteString) object;
        byte[] thisData = data;
        byte[] otherData = that.data;
        int length = thisData.length;
        if (length != otherData.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (thisData[i] != otherData[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
