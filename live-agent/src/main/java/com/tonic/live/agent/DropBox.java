package com.tonic.live.agent;

/** The hand-off slot where the JDI debugger parks object references over JDWP for the in-process agent, which clears it after each consume. */
public final class DropBox
{

    /** References parked by JDI for the agent to consume; null when empty. */
    public static volatile Object[] BOX;

    private DropBox()
    {
    }
}
