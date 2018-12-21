package ninja.javahacker.mocker;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import ninja.javahacker.reifiedgeneric.ReifiedGeneric;

/**
 * @author Victor Williams Stafusa da Silva
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Mocker<A> {

    private static final Map<Class<?>, Object> ZEROS = Map.of(
            boolean.class, Boolean.FALSE,
            char.class, '\0',
            byte.class, (byte) 0,
            short.class, (short) 0,
            int.class, 0,
            long.class, 0L,
            float.class, 0.0F,
            double.class, 0.0
    );

    @NonNull
    @Getter
    A target;

    @NonNull
    Class<A> type;

    @NonNull
    Map<String, Rule<A, ?>> actions;

    @NonNull
    List<String> names;

    @NonNull
    AtomicInteger nextAuto;

    @Data
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static class Rule<A, R> {
        @NonFinal boolean enabled;
        @NonNull Predicate<Call<A>> test;
        @NonNull Action<A, R> action;
    }

    private Mocker(@NonNull Class<A> type) {
        if (!type.isInterface()) throw new IllegalArgumentException();

        this.type = type;
        this.actions = new HashMap<>();
        this.names = new ArrayList<>();
        this.nextAuto = new AtomicInteger(0);

        var ccl = Thread.currentThread().getContextClassLoader();
        this.target = type.cast(Proxy.newProxyInstance(ccl, new Class<?>[] {type}, this::invoke));
    }

    @Nullable
    @SuppressFBWarnings("UP_UNUSED_PARAMETER")
    private Object invoke(Object instance, Method m, Object[] args) throws Throwable {
        if (args == null) args = new Object[0];
        var call = new Call<>(target, m, List.of(args));
        for (var s : names) {
            var e = actions.get(s);
            if (e.enabled && e.test.test(call)) return e.action.proccess(call);
        }
        throw new AssertionError("This call (" + m.toString() + ") is unexpected.");
    }

    @SuppressWarnings("unchecked")
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private static <R> R defaultReturnFor(Method m) {
        return (R) ZEROS.getOrDefault(m.getReturnType(), null);
    }

    public static <A> Mocker<A> mock(@NonNull Class<A> type) {
        return new Mocker<>(type);
    }

    public static <A> Mocker<A> mock(@NonNull ReifiedGeneric<A> type) {
        return new Mocker<>(type.raw());
    }

    @NonNull
    public Mocker<A> reset() {
        actions.clear();
        names.clear();
        return this;
    }

    @Synchronized
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private <R> void put(@NonNull String name, @NonNull Predicate<Call<A>> test, @NonNull Action<A, R> action) {
        actions.remove(name);
        names.remove(name);
        actions.put(name, new Rule<>(true, test, action));
        names.add(name);
    }

    @Synchronized
    public void disable(@NonNull String... names) {
        for (var s : names) {
            actions.get(s).enabled = false;
        }
    }

    @Synchronized
    public void enable(@NonNull String... names) {
        for (var s : names) {
            actions.get(s).enabled = true;
        }
    }

    @Synchronized
    public boolean exists(@NonNull String name) {
        return actions.containsKey(name);
    }

    @Synchronized
    public boolean isEnabled(@NonNull String name) {
        var r = actions.get(name);
        if (r == null) throw new IllegalArgumentException();
        return r.enabled;
    }

    public OngoingRuleDefinition<A> rule(@NonNull String name) {
        return new OngoingRuleDefinition<>(this, name);
    }

    public OngoingRuleDefinition<A> rule() {
        return rule("$AUTO$" + nextAuto.incrementAndGet());
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class OngoingRuleDefinition<A> {
        @NonNull Mocker<A> owner;
        @NonNull String rule;

        @NonNull
        @SuppressWarnings("overloads")
        public <S> Receiver<A, S> function(@NonNull Function<A, S> what) {
            Method[] choosen = new Method[1];
            InvocationHandler ih = (p, m, args) -> {
                if (choosen[0] != null) {
                    throw new IllegalArgumentException("Don't call more than one method in the given object!");
                }
                choosen[0] = m;
                return defaultReturnFor(m);
            };
            var ccl = Thread.currentThread().getContextClassLoader();
            var t = owner.type;
            var stubber = t.cast(Proxy.newProxyInstance(ccl, new Class<?>[] {t}, ih));
            what.apply(stubber);
            if (choosen[0] == null) throw new IllegalArgumentException("You didn't called anything in the given object!");
            return new Receiver<>(owner, rule, c -> c.getMethod() == choosen[0]);
        }

        @NonNull
        @SuppressWarnings("overloads")
        public VoidReceiver<A> procedure(@NonNull Consumer<A> what) {
            Method[] choosen = new Method[1];
            InvocationHandler ih = (p, m, args) -> {
                if (choosen[0] != null) {
                    throw new IllegalArgumentException("Don't call more than one method in the given object!");
                }
                choosen[0] = m;
                return defaultReturnFor(m);
            };
            var ccl = Thread.currentThread().getContextClassLoader();
            var t = owner.type;
            var stubber = t.cast(Proxy.newProxyInstance(ccl, new Class<?>[] {t}, ih));
            what.accept(stubber);
            if (choosen[0] == null) throw new IllegalArgumentException("You didn't called anything in the given object!");
            return new VoidReceiver<>(owner, rule, c -> c.getMethod() == choosen[0]);
        }

        public Receiver<A, ?> anyMethod() {
            return new Receiver<>(owner, rule, c -> true);
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Receiver<A, R> {

        @NonNull Mocker<A> owner;
        @NonNull String rule;
        @NonNull Predicate<Call<A>> condition;

        @NonNull
        public Receiver<A, R> where(@NonNull Predicate<? super Call<? super A>> cond) {
            return new Receiver<>(owner, rule, condition.and(cond));
        }

        @NonNull
        public Receiver<A, R> where(@NonNull BooleanSupplier cond) {
            return where(c -> cond.getAsBoolean());
        }

        public void executes(@NonNull Action<A, R> a) {
            owner.put(rule, condition, a);
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class VoidReceiver<A> {

        @NonNull Mocker<A> owner;
        @NonNull String rule;
        @NonNull Predicate<Call<A>> condition;

        @NonNull
        public VoidReceiver<A> where(@NonNull Predicate<? super Call<? super A>> cond) {
            return new VoidReceiver<>(owner, rule, condition.and(cond));
        }

        @NonNull
        public VoidReceiver<A> where(@NonNull BooleanSupplier cond) {
            return where(c -> cond.getAsBoolean());
        }

        public void executes(@NonNull VoidAction<A> a) {
            owner.put(rule, condition, a);
        }
    }

    @Value
    @Wither(AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Call<A> {
        @NonNull A instance;
        @NonNull Method method;
        @NonNull List<?> arguments;
    }

    @FunctionalInterface
    public static interface Action<A, R> {
        public R proccess(@NonNull Call<A> call) throws Throwable;
    }

    @FunctionalInterface
    public static interface VoidAction<A> extends Action<A, Void> {
        @Override
        public default Void proccess(@NonNull Call<A> call) throws Throwable {
            run(call);
            return null;
        }

        public void run(@NonNull Call<A> call) throws Throwable;
    }
}
