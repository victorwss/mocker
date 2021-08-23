import ninja.javahacker.mocker.Mocker;

/**
 * Defines the {@link Mocker} class which permits easy and simple mocking of interfaces.
 * @see ninja.javahacker.mocker.Mocker
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module ninja.javahacker.mocker {
    requires transitive ninja.javahacker.reifiedgeneric;
    requires transitive static lombok;
    requires transitive static com.github.spotbugs.annotations;
    exports ninja.javahacker.mocker;
}