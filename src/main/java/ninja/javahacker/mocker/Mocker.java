package ninja.javahacker.mocker;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import ninja.javahacker.reifiedgeneric.ReifiedGeneric;
import ninja.javahacker.reifiedgeneric.Token;

/**
 * Creates mock implementations of interfaces.
 *
 * <p>A mocked instance is typically created through the static methods {@link #mock(Class)},
 * {@link #mock(ReifiedGeneric)} or {@link #mock(Token)}.</p>
 *
 * <p>Any unconfigured method called on a mock instance will throw an {@link UnconfiguredMethodException}.
 * Hence, to make the mock usable, you'll need to configure it through rules.</p>
 *
 * @param <A> The type implemented by the mock.
 *
 * @author Victor Williams Stafusa da Silva
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Mocker<A> {

    /**
     * Maps each primitive type to its default uninitialized value.
     */
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

    /**
     * The mock instance.
     * -- GETTER --
     * Obtains the mock instance.
     * @return The mock instance.
     */
    @NonNull
    @Getter
    A target;

    /**
     * The mocked interface.
     * -- GETTER --
     * Obtains the mocked interface.
     * @return The mocked interface.
     */
    @NonNull
    Class<A> type;

    /**
     * The rules defined for the behaviour of the mock instance.
     */
    @NonNull
    Map<String, Rule<A, ?>> actions;

    /**
     * Represents a rule encapsulating a behaviour to be performed sometime by the mock.
     * @param <A> The type implemented by the mock.
     * @param <R> The return type of the encapsulated behaviour.
     */
    @Data
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static class Rule<A, R> {

        /**
         * If this rule is enabled or not.
         * -- GETTER --
         * Determines if the rule is enabled or not.
         * @return Wheter the rule is enabled or not.
         */
        @NonFinal boolean enabled;

        /**
         * The test that checks if this rule can be triggered.
         * -- GETTER --
         * Obtains the test that checks if this rule can be triggered.
         * @return The test that checks if this rule can be triggered.
         */
        @NonNull Predicate<Call<A>> test;

        /**
         * The behaviour of this rule.
         * -- GETTER --
         * Obtains the behaviour of this rule.
         * @return The behaviour of this rule.
         */
        @NonNull Action<A, R> action;
    }

    /**
     * Instantiates a {@code Mocker} given an interface type to be mocked.
     * @param type The interface type to be mocked.
     * @throws IllegalArgumentException If the parameter is {@code null} or does not corresponds to some interface type.
     */
    private Mocker(@NonNull Class<A> type) {
        if (!type.isInterface()) throw new IllegalArgumentException();

        this.type = type;
        this.actions = new LinkedHashMap<>();

        var ccl = Thread.currentThread().getContextClassLoader();
        this.target = type.cast(Proxy.newProxyInstance(ccl, new Class<?>[] {type}, this::invoke));
    }

    /**
     * Invocation handler for any method called in the mock.
     * @param instance The instance of the mock. It's actually unused.
     * @param m The called method.
     * @param args The arguments used to call the method.
     * @return Whathever is returned by the method or {@code null} if it is a {@code void}-typed method.
     * @throws Throwable If the called method raises any exception.
     * @throws UnconfiguredMethodException If there are no rules governing what should be the behaviour of the
     *     performed call.
     */
    @Nullable
    @Synchronized
    @SuppressFBWarnings("UP_UNUSED_PARAMETER")
    private Object invoke(Object instance, Method m, Object[] args) throws Throwable {
        if (args == null) args = new Object[0];
        var call = new Call<>(target, m, List.of(args));
        for (var e : actions.values()) {
            if (e.enabled && e.test.test(call)) return e.action.proccess(call);
        }
        throw new UnconfiguredMethodException(m);
    }

    /**
     * Obtain the default return value accordingly to the method return type.
     * <p>For {@code boolean} this is {@code false}, for other primitive types, it is zero. For anything else, it is {@code null}.</p>
     * @param <R> The return type of the method.
     * @param m The method from which we want to determine the default value for its return type.
     * @return The default value of the method's return type.
     */
    @SuppressWarnings("unchecked")
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private static <R> R defaultReturnFor(Method m) {
        return (R) ZEROS.getOrDefault(m.getReturnType(), null);
    }

    /**
     * Creates a mocker instance wrapping a mock instance implementing a given interface.
     * @param <A> The type implemented by the mock.
     * @param type The type implemented by the mock.
     * @return A new mock instance of the interface.
     * @throws IllegalArgumentException If the parameter is {@code null}.
     */
    public static <A> Mocker<A> mock(@NonNull Class<A> type) {
        return new Mocker<>(type);
    }

    /**
     * Creates a mocker instance wrapping a mock instance implementing a given type that should be an interface.
     * This method is preferable to the {@link #mock(Class)} when used in generic interfaces.
     * Otherwise, the resulting object type would be a raw type.
     * @param <A> The type implemented by the mock.
     * @param type The type implemented by the mock.
     * @return A new mock instance of the specified type.
     * @throws IllegalArgumentException If the parameter is {@code null}.
     */
    public static <A> Mocker<A> mock(@NonNull ReifiedGeneric<A> type) {
        return mock(type.asClass());
    }

    /**
     * Creates a mocker instance wrapping a mock instance implementing a given type that should be an interface.
     * This method is preferable to the {@link #mock(Class)} when used in generic interfaces.
     * Otherwise, the resulting object type would be a raw type.
     * @param <A> The type implemented by the mock.
     * @param type The type implemented by the mock.
     * @return A new mock instance of the specified type.
     * @throws IllegalArgumentException If the parameter is {@code null}.
     */
    public static <A> Mocker<A> mock(@NonNull Token<A> type) {
        return mock(type.getReified());
    }

    /**
     * Clear the configurations of the mock returning it to its starting state.
     * @return {@code this}.
     */
    @NonNull
    @Synchronized
    public Mocker<A> reset() {
        actions.clear();
        return this;
    }

    /**
     * Adds a named rule to the behaviour of the mock object.
     * @param <R> The return type of the method for which the rule produces some result.
     * @param name The name of the rule.
     * @param test A condition that must be satisfied in order to allow the rule to be triggered.
     * @param action The actual behaviour of the rule.
     * @throws IllegalArgumentException If any of the parameters is {@code null}.
     */
    @Synchronized
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private <R> void put(@NonNull String name, @NonNull Predicate<Call<A>> test, @NonNull Action<A, R> action) {
        actions.remove(name);
        actions.put(name, new Rule<>(false, test, action));
    }

    /**
     * Obtains a rule for the behaviour of this mock object, given its name.
     * @param name The name of the rule to be retrieved.
     * @return The retrieved rule.
     * @throws IllegalArgumentException If {@code name} is {@code null} or there is no rule of the given name.
     */
    @Synchronized
    private Rule<A, ?> getAction(@NonNull String name) {
        var r = actions.get(name);
        if (r == null) throw new IllegalArgumentException("No action is mapped as " + name + ". There are only " + actions.keySet());
        return r;
    }

    /**
     * Disables the rules with the given names.
     * @param names The names of the rules to disable.
     * @return {@code this}.
     * @throws IllegalArgumentException If {@code names} is {@code null} or if any of the given {@code names}
     *     do not corresponds to any previously configured rule in {@code this} object.
     */
    @Synchronized
    public Mocker<A> disable(@NonNull String... names) {
        for (var s : names) {
            getAction(s).enabled = false;
        }
        return this;
    }

    /**
     * Enables the rules with the given names.
     * @param names The names of the rules to enable.
     * @return {@code this}.
     * @throws IllegalArgumentException If {@code names} is {@code null} or if any of the given {@code names}
     *     do not corresponds to any previously configured rule in {@code this} object.
     */
    @Synchronized
    public Mocker<A> enable(@NonNull String... names) {
        for (var s : names) {
            getAction(s).enabled = true;
        }
        return this;
    }

    /**
     * Disables all the rules.
     * @return {@code this}.
     */
    @Synchronized
    public Mocker<A> disableAll() {
        actions.values().forEach(a -> a.enabled = false);
        return this;
    }

    /**
     * Enables all the rules.
     * @return {@code this}.
     */
    @Synchronized
    public Mocker<A> enableAll() {
        actions.values().forEach(a -> a.enabled = true);
        return this;
    }

    /**
     * Checks if there is any rule with the given name, regardless the fact of it being enabled or not.
     * @param name The name of the rule.
     * @return {@code true} if there is some rule with the given name, {@code false} otherwise.
     * @throws IllegalArgumentException If {@code name} is {@code null}.
     */
    @Synchronized
    public boolean exists(@NonNull String name) {
        return actions.containsKey(name);
    }

    /**
     * Checks if the rule with the given name is enabled.
     * @param name The name of the rule.
     * @return {@code true} if there exists an enabled rule with the given name, {@code false} otherwise.
     * @throws IllegalArgumentException If {@code name} is {@code null} .
     * @throws NoSuchElementException If there is no such rule as the given name.
     */
    @Synchronized
    public boolean isEnabled(@NonNull String name) {
        var r = getAction(name);
        if (r == null) throw new NoSuchElementException("No such rule " + name + ".");
        return r.enabled;
    }

    /**
     * Gives all the existing rules' names.
     * @return All the existing rules' names.
     */
    @Synchronized
    public SortedSet<String> getRules() {
        return new TreeSet<>(actions.keySet());
    }

    /**
     * Gives all the enabled rules' names.
     * @return All the enabled rules' names.
     */
    @Synchronized
    public SortedSet<String> getEnabledRules() {
        return actions
                .entrySet()
                .stream()
                .filter(e -> e.getValue().enabled)
                .map(Map.Entry::getKey)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new), Collections::unmodifiableSortedSet));
    }

    /**
     * Gives all the disabled rules' names.
     * @return All the disabled rules' names.
     */
    @Synchronized
    public SortedSet<String> getDisabledRules() {
        return actions
                .entrySet()
                .stream()
                .filter(e -> !e.getValue().enabled)
                .map(Map.Entry::getKey)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new), Collections::unmodifiableSortedSet));
    }

    /**
     * Start the definition of a new rule. Either a brand new rule or overwriting an existing one.
     * @param name The name of the rule.
     * @return A builder object for the new rule definition.
     * @throws IllegalArgumentException If {@code name} is {@code null}.
     */
    public OngoingRuleDefinition<A> rule(@NonNull String name) {
        return new OngoingRuleDefinition<>(this, name);
    }

    /**
     * A builder object for defining the behaviour of some still unspecified method.
     * @param <A> The type implemented by the mock.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class OngoingRuleDefinition<A> {

        /**
         * The {@link Mocker} for which we are defining a rule.
         */
        @NonNull Mocker<A> owner;

        /**
         * The name of the rule being defined.
         */
        @NonNull String rule;

        private Predicate<Call<A>> chooseMethod(Consumer<A> doIt) {
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
            doIt.accept(stubber);
            if (choosen[0] == null) throw new IllegalArgumentException("You didn't called anything in the given object!");
            return c -> c.getMethod() == choosen[0];
        }

        private Predicate<Call<A>> chooseMethods(Consumer<A> doIt) {
            Set<Method> choosens = new LinkedHashSet<Method>();
            InvocationHandler ih = (p, m, args) -> {
                choosens.add(m);
                return defaultReturnFor(m);
            };
            var ccl = Thread.currentThread().getContextClassLoader();
            var t = owner.type;
            var stubber = t.cast(Proxy.newProxyInstance(ccl, new Class<?>[] {t}, ih));
            doIt.accept(stubber);
            if (choosens.isEmpty()) throw new IllegalArgumentException("You didn't called anything in the given object!");
            return c -> choosens.contains(c.getMethod());
        }

        /**
         * Starts the configuration of the behaviour of some non-{@code void} method of the mock.
         * <p>The returned object should be used for further behaviour definitions and refinements.</p>
         * <p>This should be used for methods that returns something. For methods that declares {@code void} as
         * the return type, use the {@link #procedure(Consumer)} method instead.</p>
         *
         * @param <S> The return type.
         * @param what Ideally, a method reference of one of the methods of the interface to be mocked.
         *     A lambda which does a dummy call to the method is also acceptable (the actual values of the parameters are unused) and
         *     a zero, {@code null} or {@code false} will be returned accordingly to the return type of the method.
         * @return A more detailed builder object for further refining the behaviour of the given method.
         * @throws IllegalArgumentException If the given lambda ({@code what}) is {@code null} or it doesn't call any method of
         *     the given object or call methods multiple times or if it was a method reference which has nothing to do with the
         *     mock instance.
         */
        @NonNull
        @SuppressWarnings("overloads")
        public <S> Receiver<A, S> function(@NonNull Function<A, S> what) {
            return new Receiver<>(owner, rule, chooseMethod(s -> what.apply(s)));
        }

        /**
         * Starts the configuration of the behaviour of some {@code void}-typed method of the mock.
         * <p>The returned object should be used for further behaviour definitions and refinements.</p>
         * <p>This should be used for methods that declares {@code void} as the return type.</p>
         * For methods that declares something else as the the return type, use the {@link #function(Function)} method instead.
         *
         * @param what Ideally, a method reference of one of the methods of the interface to be mocked.
         *     A lambda which does a dummy call to the method is also acceptable (the actual values of the parameters are unused).
         * @return A more detailed builder object for further refining the behaviour of the given method.
         * @throws IllegalArgumentException If the given lambda ({@code what}) is {@code null} or it doesn't call any method of
         *     the given object or call methods multiple times or if it was a method reference which has nothing to do with the
         *     mock instance.
         */
        @NonNull
        @SuppressWarnings("overloads")
        public VoidReceiver<A> procedure(@NonNull Consumer<A> what) {
            return new VoidReceiver<>(owner, rule, chooseMethod(what));
        }

        /**
         * Starts the configuration of the behaviour for some methods of the mock.
         * <p>The returned object should be used for further behaviour definitions and refinements.</p>
         * @param what A closure that calls several methods of the interface to be mocked.
         *     The actual values of the parameters are unused. All of the non-{@code void} calls returns zero,
         *     {@code null} or {@code false}, accordingly to the return type of the methods.
         * @return A more detailed builder object for further refining the behaviour of the methods.
         * @throws IllegalArgumentException If the given lambda ({@code what}) is {@code null} or it doesn't call any method of
         *     the given object or if it was a method reference which has nothing to do with the mock instance.
         */
        public Receiver<A, Object> someMethods(@NonNull Consumer<A> what) {
            return new Receiver<>(owner, rule, chooseMethods(what));
        }

        /**
         * Starts the configuration of the behaviour for every method of the mock.
         * <p>The returned object should be used for further behaviour definitions and refinements.</p>
         * @return A more detailed builder object for further refining the behaviour of the methods.
         */
        public Receiver<A, Object> anyMethod() {
            return new Receiver<>(owner, rule, c -> true);
        }
    }

    /**
     * A builder object for defining the behaviour of some specific non-{@code void} method.
     * @param <A> The type implemented by the mock.
     * @param <R> The return type of the method for which the behaviour will be defined.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Receiver<A, R> {

        /**
         * The {@link Mocker} for which we are defining a rule.
         */
        @NonNull Mocker<A> owner;

        /**
         * The name of the rule being defined.
         */
        @NonNull String rule;

        /**
         * The condition that must be satisfied to allow the rule be triggered.
         */
        @NonNull Predicate<Call<A>> condition;

        /**
         * Defines a condition that must be true in order to trigger this behaviour.
         * <p>Note that as the condition is a {@link Predicate} receiving the data used to call the method,
         * this is ideal for cases where that data (specially the parameters of the method) determines at least
         * in part what is the behaviour of the method.</p>
         * @param cond The condition.
         * @return Another builder object to be used for further definitions.
         * @throws IllegalArgumentException If the supplied parameter is {@code null}.
         */
        @NonNull
        public Receiver<A, R> where(@NonNull Predicate<? super Call<? super A>> cond) {
            return new Receiver<>(owner, rule, condition.and(cond));
        }

        /**
         * Defines a condition that must be true in order to trigger this behaviour.
         * <p>Note that as the condition is a {@link BooleanSupplier}, this is ideal for cases where
         * the condition has nothing to do with any data determined by the performed call
         * (like the parameters or the given instance).</p>
         * @param cond The condition.
         * @return Another builder object to be used for further definitions.
         * @throws IllegalArgumentException If the supplied parameter is {@code null}.
         */
        @NonNull
        public Receiver<A, R> where(@NonNull BooleanSupplier cond) {
            return where(c -> cond.getAsBoolean());
        }

        /**
         * Defines what the method does afterall.
         * @param behaviour The behaviour of the method.
         * @throws IllegalArgumentException If the supplied parameter is {@code null}.
         */
        public void executes(@NonNull Action<A, R> behaviour) {
            owner.put(rule, condition, behaviour);
        }
    }

    /**
     * A builder object for defining the behaviour of some specific {@code void}-returning method.
     * @param <A> The type implemented by the mock.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class VoidReceiver<A> {

        /**
         * The {@link Mocker} for which we are defining a rule.
         */
        @NonNull Mocker<A> owner;

        /**
         * The name of the rule being defined.
         */
        @NonNull String rule;

        /**
         * The condition that must be satisfied to allow the rule be triggered.
         */
        @NonNull Predicate<Call<A>> condition;

        /**
         * Defines a condition that must be true in order to trigger this behaviour.
         * <p>Note that as the condition is a {@link Predicate} receiving the data used to call the method,
         * this is ideal for cases where that data (specially the parameters of the method) represents a context
         * that governs the behaviour of the method.</p>
         * @param cond The condition.
         * @return Another builder object to be used for further definitions.
         * @throws IllegalArgumentException If the supplied parameter is {@code null}.
         */
        @NonNull
        public VoidReceiver<A> where(@NonNull Predicate<? super Call<? super A>> cond) {
            return new VoidReceiver<>(owner, rule, condition.and(cond));
        }

        /**
         * Defines a condition that must be true in order to trigger this behaviour.
         * <p>Note that as the condition is a {@link BooleanSupplier}, this is ideal for cases where
         * the condition has nothing to do with any data determined by the performed call
         * (like the parameters or the given instance).</p>
         * @param cond The condition.
         * @return Another builder object to be used for further definitions.
         * @throws IllegalArgumentException If the supplied parameter is {@code null}.
         */
        @NonNull
        public VoidReceiver<A> where(@NonNull BooleanSupplier cond) {
            return where(c -> cond.getAsBoolean());
        }

        /**
         * Defines what the method does afterall.
         * @param behaviour The behaviour of the method.
         * @throws IllegalArgumentException If the supplied parameter is {@code null}.
         */
        public void executes(@NonNull VoidAction<A> behaviour) {
            owner.put(rule, condition, behaviour);
        }
    }

    /**
     * A command to encapsulate a call to some method in some mock instance of some interface.
     * <p>Note that this command only encapsulates the call itself and does not contains any information
     * about the behavior of the method.</p>
     * @param <A> The type of the instance for which the method is being or was called.
     */
    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Call<A> {

        /**
         * The mock instance for which some method is called.
         * -- GETTER --
         * Obtains the mock instance for which some method is called.
         * @return The mock instance for which some method is called.
         */
        @NonNull A instance;

        /**
         * The called method.
         * -- GETTER --
         * Obtains the called method.
         * @return The called method.
         */
        @NonNull Method method;

        /**
         * The real parameters of the method.
         * -- GETTER --
         * Obtains the real parameters of the method.
         * @return The real parameters of the method.
         */
        @NonNull List<?> arguments;

        /**
         * Constructs an instnce.
         * @param instance The mock instance for which some method is called.
         * @param method The called method.
         * @param arguments The real parameters of the method.
         * @throws IllegalArgumentException If any parameter is {@code null}.
         */
        public Call(@NonNull A instance, @NonNull Method method, @NonNull List<?> arguments) {
            this.instance = instance;
            this.method = method;
            this.arguments = arguments;
        }

        /**
         * Obtains the real parameters of the method as an array.
         * @implSpec Each invocation will create a new array. Subsequent modifications on the array will not change the call arguments.
         * @return The real parameters of the method as an array.
         */
        public Object[] getArgumentsArray() {
            return arguments.toArray();
        }

        /**
         * Calls the default implementation of the method.
         * @return Whatever the called method returns or {@code null} if it is {@code void}-typed.
         * @throws IllegalStateException If the method has no default implementation.
         * @throws IllegalArgumentException If the instance, the method and the arguments doesn't match as defined by
         *     {@link InvocationHandler#invokeDefault(Object, Method, Object[])}.
         * @throws Throwable Whatever the called method throws.
         */
        public Object executeDefault() throws Throwable {
            if (!method.isDefault()) throw new IllegalStateException("That is not a default method.");
            return InvocationHandler.invokeDefault(instance, method, arguments.toArray());
        }
    }

    /**
     * Represents the behaviour of the call of some mock method.
     * @param <A> The type of the mock instance where the call is performed.
     * @param <R> The return type of the called method.
     */
    @FunctionalInterface
    public static interface Action<A, R> {

        /**
         * Executes the call. The instance of {@code this} object contains the behaviour of the call.
         * @param call A command representing a call to some method.
         * @return The result of the call.
         * @throws Throwable If the method call throws an exception, it will be rethrown.
         */
        public R proccess(Call<A> call) throws Throwable;
    }

    /**
     * Represents the behaviour of the call of some mock method which declares {@code void} as the return type.
     * @param <A> The type of the mock instance where the call is performed.
     */
    @FunctionalInterface
    public static interface VoidAction<A> extends Action<A, Void> {

        /**
         * Executes the call. The instance of {@code this} object contains the behaviour of the call.
         * <p>This implementations just forwards to the {@link #run(Call) method}, which is more convenient for overriding.</p>
         * @param call A command representing a call to some method.
         * @return The result of the call.
         * @throws Throwable If the method call throws an exception, it will be rethrown.
         */
        @Override
        public default Void proccess(Call<A> call) throws Throwable {
            run(call);
            return null;
        }

        /**
         * Executes the call. The instance of {@code this} object contains the behaviour of the call.
         * @param call A command representing a call to some {@code void}-typed method.
         * @throws Throwable If the method call throws an exception, it will be rethrown.
         */
        public void run(Call<A> call) throws Throwable;
    }
}
