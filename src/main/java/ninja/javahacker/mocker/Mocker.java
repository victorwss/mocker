package ninja.javahacker.mocker;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.Value;
import lombok.experimental.FieldDefaults;
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

    private static final Action UNEXPECTED = call -> {
        var a = call.getReturned();
        var b = call.getRaised();
        if (a.isPresent() || b.isPresent()) return call;
        return call.withRaised(Optional.of(new AssertionError("This call is unexpected.")));
    };

    @NonNull
    @Getter
    A target;

    @NonNull
    Class<A> type;

    @NonNull
    Map<String, Group> actions;

    @NonNull
    List<String> names;

    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static class Group {
        @NonNull List<Predicate<? super Call>> tests;
        @NonNull Action action;
    }

    private Mocker(@NonNull Class<A> type) {
        if (!type.isInterface()) throw new IllegalArgumentException();

        this.type = type;
        this.actions = new HashMap<>();
        this.names = new ArrayList<>();

        var ccl = Thread.currentThread().getContextClassLoader();
        this.target = type.cast(Proxy.newProxyInstance(ccl, new Class<?>[] {type}, this::invoke));
    }

    @SuppressFBWarnings("UP_UNUSED_PARAMETER")
    private Object invoke(Object instance, Method m, Object[] args) {
        var c = new Call(target, m, List.of(args), Optional.empty(), Optional.empty());
        for (var s : names) {
            var e = actions.get(s);
            for (var t : e.getTests()) {
                if (!t.test(c)) return e.getAction().run(c);
            }
        }
        UNEXPECTED.run(c);
        throw new AssertionError();
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
    private void put(@NonNull String name, @NonNull List<Predicate<? super Call>> tests, @NonNull Action action) {
        actions.put(name, new Group(tests, action));
        names.add(name);
    }

    @Synchronized
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private void remove(@NonNull String name) {
        actions.remove(name);
        names.remove(name);
    }

    public OngoingRuleDefinition<A> rule(@NonNull String name) {
        return new OngoingRuleDefinition<>(this, name);
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class OngoingRuleDefinition<A> {
        @NonNull Mocker<A> owner;
        @NonNull List<Predicate<? super Call>> conditions = new ArrayList<>();
        @NonNull String rule;

        {
            conditions.add(c -> false);
        }

        @NonNull
        public OngoingRuleDefinition<A> when(@NonNull Consumer<A> what) {
            var choosen = new HashSet<Method>();
            InvocationHandler ih = (p, m, args) -> {
                if (choosen.contains(m)) {
                    throw new IllegalArgumentException("Don't call the same method more than once in the given object!");
                }
                choosen.add(m);
                return ZEROS.getOrDefault(m.getReturnType(), null);
            };
            var ccl = Thread.currentThread().getContextClassLoader();
            var t = owner.type;
            var stubber = t.cast(Proxy.newProxyInstance(ccl, new Class<?>[] {t}, ih));
            what.accept(stubber);
            if (choosen.isEmpty()) throw new IllegalArgumentException("You didn't called anything in the given object!");
            return setFor(choosen);
        }

        @NonNull
        public OngoingRuleDefinition<A> where(@NonNull Predicate<? super Call> cond) {
            conditions.add(cond);
            return this;
        }

        @NonNull
        public OngoingRuleDefinition<A> where(@NonNull BooleanSupplier cond) {
            return where(c -> cond.getAsBoolean());
        }

        public OngoingRuleDefinition<A> setFor(@NonNull Method... methods) {
            return setFor(List.of(methods));
        }

        public OngoingRuleDefinition<A> setFor(@NonNull Collection<Method> methods) {
            var cm = List.copyOf(methods);
            return where(c -> cm.contains(c.getMethod()));
        }

        public void delete() {
            owner.remove(rule);
        }

        public Receiver<A> thenDo(@NonNull Action a) {
            return new Receiver<A>(x -> owner.put(rule, conditions, x), UNEXPECTED).thenDo(a);
        }

        public Receiver<A> thenDo(@NonNull DirectAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenDo(@NonNull SimpleAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenDo(@NonNull SimplerAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenDo(@NonNull SimplestAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenFail() {
            return thenThrow(() -> new AssertionError());
        }

        public Receiver<A> thenDoNothing() {
            return thenDo((Call c) -> null);
        }

        public Receiver<A> thenReturn(Supplier<?> what) {
            return thenDo(args -> what.get());
        }

        public Receiver<A> thenThrow(Supplier<? extends Throwable> what) {
            return thenDo(args -> { throw what.get(); });
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Receiver<A> {

        @NonNull Consumer<Action> does;
        @NonNull Action previous;

        public Receiver<A> thenDo(@NonNull Action a) {
            Action b = a.append(previous);
            does.accept(b);
            return new Receiver<>(does, b);
        }

        public Receiver<A> thenDo(@NonNull DirectAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenDo(@NonNull SimpleAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenDo(@NonNull SimplerAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenDo(@NonNull SimplestAction a) {
            return thenDo((Action) a);
        }

        public Receiver<A> thenFail() {
            return thenThrow(() -> new AssertionError());
        }

        public Receiver<A> thenDoNothing() {
            return thenDo((Call c) -> null);
        }

        public Receiver<A> thenReturn(Supplier<?> what) {
            return thenDo(args -> what.get());
        }

        public Receiver<A> thenThrow(Supplier<? extends Throwable> what) {
            return thenDo(args -> { throw what.get(); });
        }
    }

    @Value
    @Wither
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Call {
        @NonNull Object instance;
        @NonNull Method method;
        @NonNull List<?> arguments;
        @NonNull Optional<Object> returned;
        @NonNull Optional<Throwable> raised;
    }

    public static interface Action {
        public Call run(@NonNull Call call);

        public default Action append(@NonNull Action p) {
            return c -> p.run(run(c));
        }

        public default Action prepend(@NonNull Action p) {
            return c -> run(p.run(c));
        }

        public static Action choose(@NonNull Predicate<? super Call> p, @NonNull Action a, @NonNull Action b) {
            return c -> (p.test(c) ? a : b).run(c);
        }
    }

    public static interface DirectAction extends Action {
        public Object run(@NonNull Object instance, @NonNull Method method, @NonNull List<?> args) throws Throwable;

        @Override
        public default Call run(@NonNull Call call) {
            try {
                return call.withReturned(Optional.ofNullable(run(call.getInstance(), call.getMethod(), call.getArguments())));
            } catch (Throwable t) {
                return call.withRaised(Optional.of(t));
            }
        }
    }

    public static interface SimpleAction extends DirectAction {
        public Object run(@NonNull List<?> args) throws Throwable;

        @Override
        public default Object run(@NonNull Object instance, @NonNull Method method, @NonNull List<?> args) throws Throwable {
            return run(args);
        }
    }

    public static interface SimplerAction extends SimpleAction {
        public Object run() throws Throwable;

        @Override
        public default Object run(@NonNull List<?> args) throws Throwable {
            return run();
        }
    }

    public static interface SimplestAction extends DirectAction {
        public void run() throws Throwable;

        @Override
        public default Object run(@NonNull Object instance, @NonNull Method method, @NonNull List<?> args) throws Throwable {
            run();
            return ZEROS.getOrDefault(method.getReturnType(), null);
        }
    }

}
