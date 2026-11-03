package com.loop.explorer.privileged;

interface IPrivilegedService {
    /** Required by Shizuku to stop the service. */
    void destroy() = 16777114;

    /** Runs a command; the first line of the answer is the exit code, the rest is the output. */
    String exec(in String[] argv) = 1;
}
