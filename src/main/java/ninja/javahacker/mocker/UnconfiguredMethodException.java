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
     * @throws IllegalArgumentException If {@code called} is {@code null}.
     */
    public UnconfiguredMethodException(@NonNull Method called) {
        super("This call (" + called + ") is unexpected.");
        this.called = called;
    }

    /**
     * Obtains which unconfigured method was called.
     * @return The unconfigured method called.
     */
    public Method getCalled() {
        return called;
    }

    /**
     * Used for custom serialization of the {@link #called} field.
     * <p>Serializes the declaring class of the method, then its name, and then its parameter types.</p>
     * @param out The object stream to where the serialization is happening.
     * @throws IOException If something goes wrong when using the {@code out}.
     * @throws IllegalArgumentException If {@code out} is {@code null}.
     */
    private void writeObject(@NonNull ObjectOutputStream out) throws IOException {
        out.writeObject(called.getDeclaringClass());
        out.writeObject(called.getName());
        out.writeObject(called.getParameterTypes());
    }

    /**
     * Used for custom deserialization of the {@link #called} field.
     * <p>Deserializes by reading the declaring class of the method, then its name, and then its parameter types.
     * Finally, locates the method and puts it in the unserialized object.</p>
     * @param in The object stream from where the desserialization is happening.
     * @throws IOException If something goes wrong when using the {@code in}, or the referenced class does not
     *     exists or the referenced method does not exists.
     * @throws IllegalArgumentException If {@code out} is {@code null}.
     */
    private void readObject(@NonNull ObjectInputStream in) throws IOException {
        try {
            Class<?> c = (Class<?>) in.readObject();
            String methodName = (String) in.readObject();
            Class<?>[] params = (Class<?>[]) in.readObject();
            this.called = c.getMethod(methodName, params);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
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
