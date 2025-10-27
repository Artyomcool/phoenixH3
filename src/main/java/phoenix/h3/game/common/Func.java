package phoenix.h3.game.common;

public abstract class Func<F,T> {
    private static final Func<Object, Object> IDENTITY = new Func<Object, Object>() {
        @Override
        public Object f(Object a) {
            return a;
        }
    };

    @SuppressWarnings("unchecked")
    public static <F,T> Func<F,T> identity() {
        return (Func<F, T>) IDENTITY;
    }

    public abstract T f(F a);
}
