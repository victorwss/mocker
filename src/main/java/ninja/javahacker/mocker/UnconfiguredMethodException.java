package ninja.javahacker.mocker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.Objects;
import lombok.NonNull;

/**
 * This is throw when an unconfigured mock method is called.
 *
 * @author Victor Williams Stafusa da Silva
 */
public final class UnconfiguredMethodException extends RuntimeException {

    /**
     * Stuff needed for serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The unconfigured method called.
     */
    private transient Method called;

    /**
     * Creates an instance of this exception.
     * @param called The unconfigured method called.
     * @throws NullPointerException If {@code called} is {@code null}.
     */
    public UnconfiguredMethodException(Method called) {
        super("This call (" + Objects.requireNonNull(called, "called is null.").toString() + ") is unexpected.");
        this.called = called;
    }

    /**
     * Obtains which unconfigured method was called.
     * @return The unconfigured method called.
     */
    public Method getCalled() {
        return called;
    }

    private void writeObject(@NonNull ObjectOutputStream out) throws IOException {
        out.writeObject(called.getDeclaringClass());
        out.writeObject(called.getName());
        out.writeObject(called.getParameterTypes());
    }

    private void readObject(@NonNull ObjectInputStream in) throws IOException, ClassNotFoundException {
        Class<?> c = (Class<?>) in.readObject();
        String methodName = (String) in.readObject();
        Class<?>[] params = (Class<?>[]) in.readObject();
        try {
            this.called = c.getMethod(methodName, params);
        } catch (NoSuchMethodException e) {
            throw new IOException(e);
        }
    }

    /**
     * {@inheritDoc}
     * @param obj {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof UnconfiguredMethodException && Objects.equals(called, ((UnconfiguredMethodException) obj).called);
    }

    /**
     * {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return called.hashCode();
    }
}
