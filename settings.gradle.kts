rootProject.name = "JStudio"

// Live JVM debugging client (attach + wire protocol to the pure-Java agent).
include(":live-client")

// The pure-Java JStudio Live agent (java.lang.instrument), bundled in the jar and attached to targets.
include(":live-agent")

// Reference GUI plugin used to validate the plugin API (built against the app; not shipped in JStudio.jar).
include(":sample-plugin")
