# Plugin runtime

Design notes for `com.tonic.plugin` and the CLI plugin loader. The code carries no comments; the reasons behind
its less obvious choices are recorded here.

- Every plugin UI contribution records an undo registration in the plugin's contribution list, so unloading a
  plugin removes everything it added. Teardown runs the removers in reverse order, then disposes the plugin, and
  isolates each step so one failure does not stop the rest.
- Plugins are activated on the EDT, and their callbacks are invoked guarded, so a failing action shows a dialog
  instead of escaping as an uncaught exception. Activation failures leave the plugin in the ERROR state.
- A jar with no `@JStudioPlugin` (a stray library) has nothing to host, so its class loader is released at once.
  Plugins from the same jar share one loader.
- A jar plugin's class loader must outlive the load call, because the plugin loads its classes lazily through
  it. Ownership passes to the wrapper, which closes the loader when the plugin is disposed; every failure path
  closes it immediately.
- Plugin-contributed components are themed recursively and re-themed on every theme switch. A switch runs
  `updateComponentTreeUI`, resetting colours to look-and-feel defaults, before listeners fire, so the explicit
  colours are applied again afterwards. Buttons keep their look-and-feel shape; push and toggle buttons are filled
  with the surface colour, and toggles read better without a filled box.
- Top-level menus a plugin created are remembered so they can be removed when they become empty again.
- The live plugin context hands implementations a shared empty project when none is loaded, so they never see
  null. VM debug sessions opened by plugins are capped; the oldest are disposed when the cap is reached, as a
  backstop against leaked handles.
- Method arguments for the VM are built recursively from argument specs on the interpreter's heap: boxed
  primitives, object or array instances, or null. A malformed primitive-array element fails with a descriptive
  `IllegalArgumentException` rather than a raw `ClassCastException`.
- Plugin classes are instantiated off the EDT, so their constructors must be trivial; do Swing work in `start`.
  Each jar's class loader asks the app loader first, so plugins can use every app class; it is shared by the jar's
  plugins and closed only on reload or shutdown. Disable keeps the classes loaded; reload re-reads the jar.
- The plugin context looks up the current project on every call, so nothing goes stale; `host.currentProject()`,
  which can be null, is the reliable open-project check.
- The live API borrows the app's live session and never closes it, since closing it would detach the whole app.
- `JStudioHost` is deliberately small: plugins reach app singletons by importing them, and the host adds only UI
  contribution plus cleanup helpers (`onEvent`, `track`) that avoid leaks. `Registration.remove` is idempotent
  because a plugin may remove a registration and the host removes it again on unload.
- The refactor and script APIs return plain result objects so callers need not depend on the host, YABR or the
  script engine; they run off the EDT (the AI chat worker), and MainFrame's handlers move UI work onto the EDT.
