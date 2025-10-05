package phoenix.h3;

import java.io.*;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class Serializer {

    // --- те же теги ---
    private static final byte T_NULL = 0;
    private static final byte T_INT = 1;
    private static final byte T_LONG = 2;
    private static final byte T_BOOLEAN = 3;
    private static final byte T_DOUBLE = 4;
    private static final byte T_FLOAT = 5;
    private static final byte T_SHORT = 6;
    private static final byte T_BYTE = 7;
    private static final byte T_CHAR = 8;
    private static final byte T_STRING = 9;

    private static final byte T_ARR_OBJ = 20;
    private static final byte T_ARR_BYTE = 21;
    private static final byte T_ARR_BOOL = 22;
    private static final byte T_ARR_SHORT = 23;
    private static final byte T_ARR_CHAR = 24;
    private static final byte T_ARR_INT = 25;
    private static final byte T_ARR_LONG = 26;
    private static final byte T_ARR_FLOAT = 27;
    private static final byte T_ARR_DOUBLE = 28;

    private static final byte T_VECTOR = 30;
    private static final byte T_HASHTABLE = 31;

    public static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        DataOutputStream out = new DataOutputStream(bos);
        writeObject(out, object);
        out.flush();
        return bos.toByteArray();
    }

    public static Object deserialize(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        return readObject(in);
    }

    private static void writeObject(DataOutputStream out, Object obj) throws IOException {
        if (obj == null) {
            out.write(T_NULL);
            return;
        }

        if (obj instanceof Integer) {
            out.write(T_INT);
            out.writeInt((Integer) obj);
        } else if (obj instanceof Long) {
            out.write(T_LONG);
            out.writeLong((Long) obj);
        } else if (obj instanceof Boolean) {
            out.write(T_BOOLEAN);
            out.writeBoolean((Boolean) obj);
        } else if (obj instanceof Double) {
            out.write(T_DOUBLE);
            out.writeDouble((Double) obj);
        } else if (obj instanceof Float) {
            out.write(T_FLOAT);
            out.writeFloat((Float) obj);
        } else if (obj instanceof Short) {
            out.write(T_SHORT);
            out.writeShort((Short) obj);
        } else if (obj instanceof Byte) {
            out.write(T_BYTE);
            out.writeByte((Byte) obj);
        } else if (obj instanceof Character) {
            out.write(T_CHAR);
            out.writeChar((Character) obj);
        } else if (obj instanceof String) {
            out.write(T_STRING);
            out.writeUTF((String) obj);
        } else if (obj instanceof byte[]) {
            byte[] a = (byte[]) obj;
            out.write(T_ARR_BYTE);
            out.writeInt(a.length);
            out.write(a, 0, a.length);
        } else if (obj instanceof boolean[]) {
            boolean[] a = (boolean[]) obj;
            out.write(T_ARR_BOOL);
            out.writeInt(a.length);
            for (boolean b : a) {
                out.writeBoolean(b);
            }
        } else if (obj instanceof short[]) {
            short[] a = (short[]) obj;
            out.write(T_ARR_SHORT);
            out.writeInt(a.length);
            for (short value : a) {
                out.writeShort(value);
            }
        } else if (obj instanceof char[]) {
            char[] a = (char[]) obj;
            out.write(T_ARR_CHAR);
            out.writeInt(a.length);
            for (char c : a) {
                out.writeChar(c);
            }
        } else if (obj instanceof int[]) {
            int[] a = (int[]) obj;
            out.write(T_ARR_INT);
            out.writeInt(a.length);
            for (int j : a) {
                out.writeInt(j);
            }
        } else if (obj instanceof long[]) {
            long[] a = (long[]) obj;
            out.write(T_ARR_LONG);
            out.writeInt(a.length);
            for (long l : a) {
                out.writeLong(l);
            }
        } else if (obj instanceof float[]) {
            float[] a = (float[]) obj;
            out.write(T_ARR_FLOAT);
            out.writeInt(a.length);
            for (float v : a) {
                out.writeFloat(v);
            }
        } else if (obj instanceof double[]) {
            double[] a = (double[]) obj;
            out.write(T_ARR_DOUBLE);
            out.writeInt(a.length);
            for (double v : a) {
                out.writeDouble(v);
            }
        } else if (obj instanceof Object[]) {
            Object[] a = (Object[]) obj;
            out.write(T_ARR_OBJ);
            out.writeInt(a.length);
            for (Object object : a) {
                writeObject(out, object);
            }
        } else if (obj instanceof Vector) {
            Vector<?> v = (Vector<?>) obj;
            out.write(T_VECTOR);
            out.writeInt(v.size());
            for (int i = 0; i < v.size(); i++) {
                writeObject(out, v.get(i));
            }
        } else if (obj instanceof Hashtable) {
            Hashtable<?, ?> ht = (Hashtable<?, ?>) obj;
            out.write(T_HASHTABLE);
            out.writeInt(ht.size());
            for (Enumeration<?> k = ht.keys(), v = ht.elements(); k.hasMoreElements(); ) {
                Object key = k.nextElement();
                writeObject(out, key);
                writeObject(out, v.nextElement());
            }
        } else {
            throw new IOException(new StringBuffer("Unsupported type: ").append(obj.getClass().getName()).toString());
        }
    }

    private static Object readObject(DataInputStream in) throws IOException {
        int tag = in.read();
        switch (tag) {
            case T_NULL:
                return null;
            case T_INT:
                return in.readInt();
            case T_LONG:
                return in.readLong();
            case T_BOOLEAN:
                return in.readBoolean();
            case T_DOUBLE:
                return in.readDouble();
            case T_FLOAT:
                return in.readFloat();
            case T_SHORT:
                return in.readShort();
            case T_BYTE:
                return in.readByte();
            case T_CHAR:
                return in.readChar();
            case T_STRING:
                return in.readUTF();

            case T_ARR_BYTE: {
                int n = in.readInt();
                byte[] a = new byte[n];
                in.readFully(a);
                return a;
            }
            case T_ARR_BOOL: {
                int n = in.readInt();
                boolean[] a = new boolean[n];
                for (int i = 0; i < n; i++) {
                    a[i] = in.readBoolean();
                }
                return a;
            }
            case T_ARR_SHORT: {
                int n = in.readInt();
                short[] a = new short[n];
                for (int i = 0; i < n; i++) {
                    a[i] = in.readShort();
                }
                return a;
            }
            case T_ARR_CHAR: {
                int n = in.readInt();
                char[] a = new char[n];
                for (int i = 0; i < n; i++) {
                    a[i] = in.readChar();
                }
                return a;
            }
            case T_ARR_INT: {
                int n = in.readInt();
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case T_ARR_LONG: {
                int n = in.readInt();
                long[] a = new long[n];
                for (int i = 0; i < n; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            case T_ARR_FLOAT: {
                int n = in.readInt();
                float[] a = new float[n];
                for (int i = 0; i < n; i++) {
                    a[i] = in.readFloat();
                }
                return a;
            }
            case T_ARR_DOUBLE: {
                int n = in.readInt();
                double[] a = new double[n];
                for (int i = 0; i < n; i++) {
                    a[i] = in.readDouble();
                }
                return a;
            }
            case T_ARR_OBJ: {
                int n = in.readInt();
                Object[] a = new Object[n];
                for (int i = 0; i < n; i++) {
                    a[i] = readObject(in);
                }
                return a;
            }
            case T_VECTOR: {
                int n = in.readInt();
                Vector<Object> v = new Vector<>(n);
                for (int i = 0; i < n; i++) {
                    v.add(readObject(in));
                }
                return v;
            }
            case T_HASHTABLE: {
                int n = in.readInt();
                Hashtable<Object, Object> ht = new Hashtable<>(n);
                for (int i = 0; i < n; i++) {
                    Object k = readObject(in);
                    Object val = readObject(in);
                    ht.put(k, val);
                }
                return ht;
            }
            default:
                throw new IOException(new StringBuffer("Unknown tag: ").append(tag).toString());
        }
    }
}
