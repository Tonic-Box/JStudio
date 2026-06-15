rootProject.name = "JStudio"

// Live JVM debugging client (attach + wire protocol to the pure-Java agent).
include(":live-client")

// The pure-Java JStudio Live agent (java.lang.instrument), bundled in the jar and attached to targets.
include(":live-agent")
