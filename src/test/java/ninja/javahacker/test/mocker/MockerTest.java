package ninja.javahacker.test.mocker;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import ninja.javahacker.mocker.Mocker;
import ninja.javahacker.mocker.UnconfiguredMethodException;
import ninja.javahacker.reifiedgeneric.ReifiedGeneric;
import ninja.javahacker.reifiedgeneric.Token;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * @author Victor Williams Stafusa da Silva
 */
public class MockerTest {

    private static interface MyInterface {

        public void voidParameterlessMethod();

        public int returningParameterlessMethod();

        public void voidParameteredMethod(int x);

        public int returningParameteredMethod(int x);

        public default int methodWithDefaultImplementation(int x) {
            return 42 + x;
        }

        public String returningMultiparameteredMethod(int x, String y);

        public String overloadedMethod(String x);

        public int overloadedMethod(int x);

        public void overloadedMethod(String x, String y);
    }

    private Mocker<MyInterface> fixture1() {
        var controller = Mocker.mock(MyInterface.class);
        controller.rule("banana").function(MyInterface::returningParameterlessMethod).executes(c -> 42);
        controller.enable("banana");
        return controller;
    }

    private Mocker<MyInterface> fixture2() {
        return Mocker.mock(MyInterface.class);
    }

    private Mocker<MyInterface> fixture3() {
        var controller = Mocker.mock(MyInterface.class);
        controller.rule("banana").function(MyInterface::returningParameterlessMethod).executes(c -> {
            controller.disableAll().enable("apple");
            return 99;
        });
        controller.rule("apple").function(x -> x.returningParameteredMethod(0)).executes(c -> {
            controller.disableAll().enable("strawberry");
            return 88;
        });
        controller.rule("strawberry").function(x -> x.returningMultiparameteredMethod(0, "")).executes(c -> {
            controller.disableAll().enable("lemon");
            return "ok";
        });
        controller.rule("lemon").procedure(x -> x.voidParameteredMethod(0)).executes(c -> controller.disableAll());
        controller.enable("banana");
        return controller;
    }

    private Mocker<MyInterface> fixture4() {
        Predicate<Mocker.Call<?>> even = c -> ((Integer) c.getArguments().get(0)) % 2 == 0;
        Predicate<Mocker.Call<?>> odd = c -> ((Integer) c.getArguments().get(0)) % 2 != 0;
        var controller = Mocker.mock(MyInterface.class);
        controller.rule("pear").function(x -> x.returningParameteredMethod(0)).where(even).executes(c -> 123);
        controller.rule("peach").function(x -> x.returningParameteredMethod(0)).where(odd).executes(c -> 321);
        controller.enableAll();
        return controller;
    }

    private void checkRules(Mocker<MyInterface> controller, Set<String> enabled, Set<String> disabled) {
        Set<String> all = new HashSet<>();
        all.addAll(enabled);
        all.addAll(disabled);
        Assertions.assertFalse(controller.exists("does-not-exists"));
        Assertions.assertFalse(controller.exists("does-not-exists-too"));
        Assertions.assertAll("exists", all.stream().map(x -> () -> Assertions.assertTrue(controller.exists(x))));
        Assertions.assertAll("sets",
                () -> Assertions.assertEquals(all, controller.getRules()),
                () -> Assertions.assertEquals(enabled, controller.getEnabledRules()),
                () -> Assertions.assertEquals(disabled, controller.getDisabledRules())
        );
        Assertions.assertFalse(controller.exists("does-not-exists"));
        Assertions.assertFalse(controller.exists("does-not-exists-either"));
    }

    @Test
    public void testStateChanging() {
        var controller = fixture3();
        checkRules(controller, Set.of("banana"), Set.of("strawberry", "apple", "lemon"));
        controller.enable("strawberry");
        checkRules(controller, Set.of("banana", "strawberry"), Set.of("apple", "lemon"));
        controller.disableAll();
        checkRules(controller, Set.of(), Set.of("banana", "apple", "strawberry", "lemon"));
        controller.enable("apple");
        checkRules(controller, Set.of("apple"), Set.of("strawberry", "banana", "lemon"));
        controller.enableAll();
        checkRules(controller, Set.of("banana", "apple", "strawberry", "lemon"), Set.of());
    }

    @Test
    public void testReset() {
        var controller = fixture3();
        checkRules(controller, Set.of("banana"), Set.of("strawberry", "apple", "lemon"));
        controller.reset();
        checkRules(controller, Set.of(), Set.of());
    }

    @Test
    public void testSimpleConfigurationFunctionMethodReference() {
        var controller = fixture1();
        checkRules(controller, Set.of("banana"), Set.of());
        Assertions.assertEquals(42, controller.getTarget().returningParameterlessMethod());
    }

    @Test
    public void testUnconfiguredFunction() {
        var controller = Mocker.mock(MyInterface.class);
        var target = controller.getTarget();
        checkRules(controller, Set.of(), Set.of());
        Assertions.assertAll("not-configured",
                () -> Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.returningParameterlessMethod()),
                () -> Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.returningParameteredMethod(8)),
                () -> Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.voidParameterlessMethod()),
                () -> Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.voidParameteredMethod(5))
        );
    }

    @Test
    public void testCallingWrongFunction() {
        var controller = fixture1();
        checkRules(controller, Set.of("banana"), Set.of());
        Assertions.assertThrows(UnconfiguredMethodException.class, () -> controller.getTarget().returningParameteredMethod(25));
    }

    @Test
    public void testRuleChanging() {
        var controller = fixture3();
        var target = controller.getTarget();

        checkRules(controller, Set.of("banana"), Set.of("strawberry", "apple", "lemon"));
        Assertions.assertEquals(99, target.returningParameterlessMethod());
        Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.returningParameterlessMethod());

        checkRules(controller, Set.of("apple"), Set.of("strawberry", "banana", "lemon"));
        Assertions.assertEquals(88, target.returningParameteredMethod(5));
        Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.returningParameteredMethod(5));

        checkRules(controller, Set.of("strawberry"), Set.of("apple", "banana", "lemon"));
        Assertions.assertEquals("ok", target.returningMultiparameteredMethod(8, "a"));
        Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.returningMultiparameteredMethod(6, "b"));

        checkRules(controller, Set.of("lemon"), Set.of("apple", "banana", "strawberry"));
        target.voidParameteredMethod(8);
        Assertions.assertThrows(UnconfiguredMethodException.class, () -> target.voidParameteredMethod(8));

        checkRules(controller, Set.of(), Set.of("apple", "banana", "strawberry", "lemon"));
    }

    @Test
    public void testMultipleRulesSameMethod() {
        var controller = fixture4();
        var target = controller.getTarget();
        Assertions.assertAll("behaviour",
                () -> checkRules(controller, Set.of("pear", "peach"), Set.of()),
                () -> Assertions.assertEquals(321, target.returningParameteredMethod(1)),
                () -> Assertions.assertEquals(123, target.returningParameteredMethod(2)),
                () -> Assertions.assertEquals(321, target.returningParameteredMethod(3)),
                () -> Assertions.assertEquals(123, target.returningParameteredMethod(4))
        );
    }
}